package com.kraft.recommend;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoBitmask;
import com.kraft.common.lotto.LottoNumberCodec;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.springframework.stereotype.Component;

/**
 * TD-008 3단계: {@link LottoRecommendationService}에서 전략 디스패치와 후보 생성(무작위
 * 샘플링, 형태 균형 정렬, 비인기도 최적화)을 분리했다. REF-01: 1~2단계와 함께 Spring 빈으로
 * 전환했다 — 프로덕션 조립은 Spring이 맡고, 테스트는 여전히 {@code new}로 직접 만든다.
 *
 * {@code randomSource}는 필드로 갖지 않고 호출마다 파라미터로 받는다 — {@code
 * LottoRecommendationService.setRandomSource(...)}가 패키지 프라이빗 테스트 전용
 * 계약이라 그대로 outer 클래스에 남아 있고, 이 클래스가 자체 필드로 캐시하면 세터
 * 호출과의 동기화 버그 여지가 생긴다. 매 호출마다 최신 값을 그대로 전달받으면 그런
 * 여지 자체가 없다.
 */
@Component
final class RecommendationCandidateGenerator {

    private static final int MAX_ATTEMPTS = 100;
    private static final int PRIZE_CANDIDATE_POOL = 50;
    private static final int BALANCED_CANDIDATE_POOL = 50;

    private final LottoNumberCodec lottoNumberCodec;
    private final CombinationScorer combinationScorer;
    private final BalancedScorer balancedScorer;
    private final MeterRegistry meterRegistry;

    RecommendationCandidateGenerator(LottoNumberCodec lottoNumberCodec, CombinationScorer combinationScorer,
                                     BalancedScorer balancedScorer, MeterRegistry meterRegistry) {
        this.lottoNumberCodec = lottoNumberCodec;
        this.combinationScorer = combinationScorer;
        this.balancedScorer = balancedScorer;
        this.meterRegistry = meterRegistry;
    }

    List<RecommendationItemView> sampleRecommendations(RecommendationRequestValidator.RequestContext ctx,
            RecommendationHistorySnapshotManager.HistorySnapshot snapshot, IntUnaryOperator randomSource) {
        return switch (ctx.strategy()) {
            case LottoRecommendationService.STRATEGY_BALANCED -> sampleBalanced(ctx, snapshot, randomSource);
            case LottoRecommendationService.STRATEGY_REDUCE_SHARED_WINNER_RISK ->
                    sampleSimple(ctx, snapshot, true, randomSource);
            default -> sampleSimple(ctx, snapshot, false, randomSource);
        };
    }

    private List<RecommendationItemView> sampleSimple(RecommendationRequestValidator.RequestContext ctx,
            RecommendationHistorySnapshotManager.HistorySnapshot snapshot, boolean reduceSharedWinnerRisk,
            IntUnaryOperator randomSource) {
        List<Integer> candidates = buildCandidates(ctx.excluded(), ctx.locked());
        List<List<Integer>> recommendations = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int attempts = 0;
        int maxAttempts = ctx.count() * MAX_ATTEMPTS;
        Set<Long> historicalMasks = snapshot.masks();
        while (recommendations.size() < ctx.count() && attempts++ < maxAttempts) {
            List<Integer> candidate = reduceSharedWinnerRisk
                    ? generateBest(candidates, historicalMasks, ctx.locked(), randomSource)
                    : generateOne(candidates, historicalMasks, ctx.locked(), randomSource);
            if (candidate != null && seen.add(LottoBitmask.of(candidate))) {
                recommendations.add(candidate);
            }
        }
        // 이론상 가능한 조합 수(allowedPossible) 안에서도, 역대 당첨 조합 회피와 중복 회피가
        // 겹쳐 maxAttempts 안에 count만큼 못 채울 수 있다 — 부족분을 조용히 반환하지 않고 명시한다.
        if (recommendations.size() < ctx.count()) {
            throw fail(ApiErrorCode.INSUFFICIENT_UNIQUE_COMBINATIONS,
                    "생성 가능한 고유 조합이 부족합니다(생성 %d / 요청 %d)."
                            .formatted(recommendations.size(), ctx.count()));
        }
        List<RecommendationItemView> items = new ArrayList<>();
        int position = 1;
        for (List<Integer> numbers : recommendations) {
            items.add(new RecommendationItemView(position++, numbers, null, List.of()));
        }
        return items;
    }

    /**
     * 무작위로 유일한 후보를 최대한 모아 형태 균형 점수로 정렬한 뒤 상위 count개를 반환한다.
     * 다른 전략과 동일하게, count만큼 채우지 못하면 부분 결과를 성공으로 위장하지 않고
     * INSUFFICIENT_UNIQUE_COMBINATIONS로 실패한다.
     */
    private List<RecommendationItemView> sampleBalanced(RecommendationRequestValidator.RequestContext ctx,
            RecommendationHistorySnapshotManager.HistorySnapshot snapshot, IntUnaryOperator randomSource) {
        List<Integer> candidates = buildCandidates(ctx.excluded(), ctx.locked());
        Set<Long> historicalMasks = snapshot.masks();
        Set<Long> seen = new HashSet<>();
        List<List<Integer>> pool = new ArrayList<>();
        int poolTarget = Math.max(BALANCED_CANDIDATE_POOL, ctx.count() * 10);
        int attempts = 0;
        int maxAttempts = poolTarget * MAX_ATTEMPTS;
        while (pool.size() < poolTarget && attempts++ < maxAttempts) {
            List<Integer> candidate = generateOne(candidates, historicalMasks, ctx.locked(), randomSource);
            if (candidate != null && seen.add(LottoBitmask.of(candidate))) {
                pool.add(candidate);
            }
        }

        List<RecommendationItemView> scored = new ArrayList<>();
        for (List<Integer> candidate : pool) {
            BalancedEvaluation evaluation = balancedScorer.evaluate(candidate);
            scored.add(new RecommendationItemView(0, candidate, evaluation.score(), evaluation.explanationCodes()));
        }
        scored.sort((a, b) -> b.score() - a.score());

        List<RecommendationItemView> result = new ArrayList<>();
        int position = 1;
        for (RecommendationItemView item : scored.stream().limit(ctx.count()).toList()) {
            result.add(new RecommendationItemView(position++, item.numbers(), item.score(), item.explanationCodes()));
        }
        if (result.size() < ctx.count()) {
            throw fail(ApiErrorCode.INSUFFICIENT_UNIQUE_COMBINATIONS,
                    "생성 가능한 고유 조합이 부족합니다(생성 %d / 요청 %d)."
                            .formatted(result.size(), ctx.count()));
        }
        return result;
    }

    /**
     * 후보 풀에서 비인기도 점수가 가장 높은 조합을 반환한다.
     * 공동 당첨자를 최소화해 개인 수령액을 최대화하는 목적.
     * generateOne이 충돌 상한 도달로 null을 반환하면(과거 1등과의 충돌을 해소하지 못함)
     * 그 시도는 후보 풀에서 제외한다 — 점수 비교 대상에 과거 1등 조합이 섞이면 안 된다.
     */
    private List<Integer> generateBest(List<Integer> candidates, Set<Long> historicalMasks, List<Integer> locked,
            IntUnaryOperator randomSource) {
        List<Integer> best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < PRIZE_CANDIDATE_POOL; i++) {
            List<Integer> candidate = generateOne(candidates, historicalMasks, locked, randomSource);
            if (candidate == null) {
                continue;
            }
            int score = combinationScorer.score(candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static List<Integer> buildCandidates(Set<Integer> excluded, List<Integer> locked) {
        Set<Integer> lockedSet = new HashSet<>(locked);
        List<Integer> candidates = new ArrayList<>(45 - excluded.size() - lockedSet.size());
        for (int i = 1; i <= 45; i++) {
            if (!excluded.contains(i) && !lockedSet.contains(i)) {
                candidates.add(i);
            }
        }
        return candidates;
    }

    // 부분 Fisher-Yates(k = 6 - locked.size()): candidates(고정·제외 번호를 뺀 나머지 풀)의
    // 앞 k개 위치만 셔플해 O(n) → O(k)로 단축한 뒤 고정 번호와 합쳐 최종 조합을 만든다.
    // candidates 배열을 호출 간 재사용하므로 요청당 한 번만 빌드한다.
    // MAX_ATTEMPTS 안에 과거 1등과 겹치지 않는 조합을 못 찾으면, 마지막 셔플 결과를
    // 그대로 반환하지 않고 null을 돌려준다 — 과거 1등 조합이 추천으로 새어나가지 않도록
    // 호출자가 이 시도를 버리고 재시도하거나 최종적으로 명시적 오류를 낸다.
    private List<Integer> generateOne(List<Integer> candidates, Set<Long> historicalMasks, List<Integer> locked,
            IntUnaryOperator randomSource) {
        int n = candidates.size();
        int need = 6 - locked.size();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            for (int i = 0; i < need; i++) {
                int j = i + randomSource.applyAsInt(n - i);
                int tmp = candidates.get(i);
                candidates.set(i, candidates.get(j));
                candidates.set(j, tmp);
            }
            List<Integer> combined = new ArrayList<>(locked);
            combined.addAll(candidates.subList(0, need));
            List<Integer> result = lottoNumberCodec.normalize(combined);
            if (!historicalMasks.contains(LottoBitmask.of(result))) {
                return result;
            }
            meterRegistry.counter("kraft_lotto_recommend_historical_collisions_total").increment();
        }
        return null;
    }

    private ApiException fail(ApiErrorCode errorCode, String message) {
        meterRegistry.counter("kraft_lotto_recommend_failures_total", "code", errorCode.name()).increment();
        return new ApiException(errorCode, message);
    }
}
