package com.kraft.recommend.service;

import com.kraft.recommend.domain.BalancedScorer;
import com.kraft.recommend.domain.CombinationScorer;
import com.kraft.recommend.domain.ExplanationCode;
import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationGenerationLimitException;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 세 전략의 후보 생성(03문서 4절 POL-RANDOM/POL-BALANCED/POL-CHOICE, 6절 반복 상한). 점수
 * 계산·번호 집합 연산은 {@link BalancedScorer}/{@link CombinationScorer}(순수 로직)에 위임하고,
 * 이 클래스는 표본 추출·상한 관리만 담당한다.
 */
@Component
@RequiredArgsConstructor
public class RecommendationCandidateGenerator {

    private static final int HISTORY_RETRY_CAP = 100;
    private static final int CHOICE_CANDIDATE_COMPARISON_CAP = 50;

    /**
     * 이 개수 이하로 조합 공간이 좁으면 무작위 추출 대신 전수 나열로 전환한다(개선 보고서
     * PERF-01). 좁은 공간에서는 "뽑고 충돌하면 다시 뽑는" 방식이 이미 뽑은 조합과 계속
     * 부딪혀 {@code collectionCap}만 소진하고 실패할 수 있다 — 고정 번호가 많거나 제외 번호가
     * 많은 요청에서 실제로 관측됐다. 2000은 {@code C(45,6)}(약 800만) 대비 충분히 작아 나열
     * 비용이 무시할 만하면서도, 이 임계값 아래에서 실제로 문제가 되던 요청들을 모두 포괄한다.
     */
    private static final int SMALL_SPACE_ENUMERATION_THRESHOLD = 2000;

    public record BalancedCandidate(LottoNumbers numbers, int score, List<ExplanationCode> explanationCodes) {
    }

    private final RecommendationRandomSource randomSource;

    public List<LottoNumbers> generateRandom(NormalizedRecommendationRequest request,
                                              RecommendationHistorySnapshot snapshot,
                                              long allowedPossible) {
        List<Integer> pool = freePool(request);
        Random random = randomSource.current();

        if (allowedPossible <= SMALL_SPACE_ENUMERATION_THRESHOLD) {
            List<LottoNumbers> universe = enumerateNonHistorical(pool, request.lockedNumbers(), snapshot);
            requireEnough(universe, request.count(), "random");
            Collections.shuffle(universe, random);
            return new ArrayList<>(universe.subList(0, request.count()));
        }

        Set<Long> usedMasks = new HashSet<>();
        List<LottoNumbers> results = new ArrayList<>();

        int attempts = 0;
        int collectionCap = request.count() * 100;
        while (results.size() < request.count()) {
            if (attempts >= collectionCap) {
                throw new RecommendationGenerationLimitException("요청 개수를 채우지 못했습니다(random).");
            }
            attempts++;
            LottoNumbers candidate = drawNonHistorical(pool, request.lockedNumbers(), snapshot, random);
            if (candidate != null && usedMasks.add(candidate.mask())) {
                results.add(candidate);
            }
        }
        return results;
    }

    public List<BalancedCandidate> generateBalanced(NormalizedRecommendationRequest request,
                                                      RecommendationHistorySnapshot snapshot,
                                                      long allowedPossible) {
        List<Integer> pool = freePool(request);
        Random random = randomSource.current();

        if (allowedPossible <= SMALL_SPACE_ENUMERATION_THRESHOLD) {
            List<LottoNumbers> universe = enumerateNonHistorical(pool, request.lockedNumbers(), snapshot);
            requireEnough(universe, request.count(), "balanced");
            List<BalancedCandidate> scored = scoreAll(universe);
            scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
            return scored.subList(0, request.count());
        }

        // allowedPossible로 목표치를 눌러 담아, 가능한 조합보다 훨씬 큰 목표를 향해 무작위로
        // 계속 뽑으며 충돌만 반복하지 않게 한다(PERF-01).
        int target = (int) Math.min(Math.max(50, (long) request.count() * 10), allowedPossible);
        int collectionCap = target * 100;

        Set<Long> usedMasks = new HashSet<>();
        List<BalancedCandidate> pooled = new ArrayList<>();

        int attempts = 0;
        while (pooled.size() < target && attempts < collectionCap) {
            attempts++;
            LottoNumbers candidate = drawNonHistorical(pool, request.lockedNumbers(), snapshot, random);
            if (candidate == null || !usedMasks.add(candidate.mask())) {
                continue;
            }
            BalancedScorer.Evaluation evaluation = BalancedScorer.evaluate(candidate.numbers());
            pooled.add(new BalancedCandidate(candidate, evaluation.score(), evaluation.codes()));
        }

        if (pooled.size() < request.count()) {
            throw new RecommendationGenerationLimitException("요청 개수를 채우지 못했습니다(balanced).");
        }

        pooled.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return pooled.subList(0, request.count());
    }

    public List<LottoNumbers> generateReduceSharedWinnerRisk(NormalizedRecommendationRequest request,
                                                               RecommendationHistorySnapshot snapshot,
                                                               long allowedPossible) {
        List<Integer> pool = freePool(request);
        Random random = randomSource.current();

        if (allowedPossible <= SMALL_SPACE_ENUMERATION_THRESHOLD) {
            List<LottoNumbers> universe = enumerateNonHistorical(pool, request.lockedNumbers(), snapshot);
            requireEnough(universe, request.count(), "reduce_shared_winner_risk");
            return pickBestOfFiftyFromUniverse(universe, random, request.count());
        }

        Set<Long> usedMasks = new HashSet<>();
        List<LottoNumbers> results = new ArrayList<>();

        int attempts = 0;
        int collectionCap = request.count() * 100;
        while (results.size() < request.count()) {
            if (attempts >= collectionCap) {
                throw new RecommendationGenerationLimitException("요청 개수를 채우지 못했습니다(reduce_shared_winner_risk).");
            }
            attempts++;
            LottoNumbers best = pickBestOfFifty(pool, request.lockedNumbers(), snapshot, random, usedMasks);
            if (best != null && usedMasks.add(best.mask())) {
                results.add(best);
            }
        }
        return results;
    }

    private void requireEnough(List<LottoNumbers> universe, int count, String strategyLabel) {
        if (universe.size() < count) {
            throw new RecommendationGenerationLimitException("요청 개수를 채우지 못했습니다(" + strategyLabel + ").");
        }
    }

    private List<BalancedCandidate> scoreAll(List<LottoNumbers> combos) {
        List<BalancedCandidate> scored = new ArrayList<>(combos.size());
        for (LottoNumbers combo : combos) {
            BalancedScorer.Evaluation evaluation = BalancedScorer.evaluate(combo.numbers());
            scored.add(new BalancedCandidate(combo, evaluation.score(), evaluation.codes()));
        }
        return scored;
    }

    /**
     * 좁은 조합 공간에서도 {@link #pickBestOfFifty}와 같은 "50개 중 최고" 선택 규칙을 유지한다
     * (개선 보고서 PERF-01). 무작위로 다시 뽑는 대신, 전수 나열한 조합을 한 번 섞은 뒤 남은
     * 후보의 앞쪽 최대 50개 중 최고점 하나만 뽑아 제거하기를 반복한다 — 뽑은 것만 빠지고
     * 나머지는 다음 회차에 다시 비교 대상이 되므로, 후보가 {@code count * 50}개보다 적은
     * 좁은 공간에서도 남은 후보를 버리지 않고 다 채울 수 있다.
     */
    private List<LottoNumbers> pickBestOfFiftyFromUniverse(List<LottoNumbers> universe, Random random, int count) {
        List<LottoNumbers> remaining = new ArrayList<>(universe);
        Collections.shuffle(remaining, random);

        // 매 회 남은 후보의 앞쪽 최대 50개만 비교해 최고점 하나를 뽑아 제거한다 — 배치 전체를
        // 건너뛰면(고른 하나를 뺀 나머지를 그냥 버리면) 후보가 count*50개보다 적은 좁은 공간에서
        // 남은 후보가 있는데도 채우지 못하고 실패한다.
        List<LottoNumbers> results = new ArrayList<>();
        while (results.size() < count && !remaining.isEmpty()) {
            int windowSize = Math.min(CHOICE_CANDIDATE_COMPARISON_CAP, remaining.size());
            int bestIndex = 0;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < windowSize; i++) {
                int score = CombinationScorer.score(remaining.get(i).numbers());
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = i;
                }
            }
            results.add(remaining.remove(bestIndex));
        }

        if (results.size() < count) {
            throw new RecommendationGenerationLimitException("요청 개수를 채우지 못했습니다(reduce_shared_winner_risk).");
        }
        return results;
    }

    /**
     * 자유 번호 풀에서 고정 번호를 뺀 나머지를 {@code needed}개씩 뽑는 모든 조합을 나열하고,
     * 과거 당첨 조합과 일치하는 것만 제외한다. {@link #SMALL_SPACE_ENUMERATION_THRESHOLD} 이하로
     * 좁은 공간에서만 호출하므로 나열 비용이 작다.
     */
    private List<LottoNumbers> enumerateNonHistorical(List<Integer> pool, Set<Integer> locked,
                                                        RecommendationHistorySnapshot snapshot) {
        int need = LottoNumbers.SIZE - locked.size();
        List<LottoNumbers> universe = new ArrayList<>();
        Consumer<List<Integer>> onCombo = free -> {
            List<Integer> picked = new ArrayList<>(locked);
            picked.addAll(free);
            LottoNumbers candidate = LottoNumbers.of(picked);
            if (!snapshot.winningMasks().contains(candidate.mask())) {
                universe.add(candidate);
            }
        };
        combinations(pool, need, 0, new ArrayDeque<>(), onCombo);
        return universe;
    }

    private void combinations(List<Integer> pool, int need, int start, Deque<Integer> current,
                               Consumer<List<Integer>> onCombo) {
        if (current.size() == need) {
            onCombo.accept(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < pool.size(); i++) {
            current.addLast(pool.get(i));
            combinations(pool, need, i + 1, current, onCombo);
            current.removeLast();
        }
    }

    /**
     * 이미 결과에 포함된 조합({@code usedMasks})은 후보 비교에서 제외한다(B03). 좁은 조합
     * 공간에서는 "50개 중 최고"가 매번 같은(이미 쓴) 조합으로 수렴할 수 있어, 이 필터가 없으면
     * 호출부의 {@code usedMasks.add}가 계속 실패해 {@code collectionCap}만 소진하고
     * {@link RecommendationGenerationLimitException}이 던져진다 — 실제로는 충분한 고유 조합이
     * 남아 있는데도 그렇다.
     */
    private LottoNumbers pickBestOfFifty(List<Integer> pool, Set<Integer> locked,
                                          RecommendationHistorySnapshot snapshot, Random random,
                                          Set<Long> usedMasks) {
        LottoNumbers best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < CHOICE_CANDIDATE_COMPARISON_CAP; i++) {
            LottoNumbers candidate = drawNonHistorical(pool, locked, snapshot, random);
            if (candidate == null || usedMasks.contains(candidate.mask())) {
                continue;
            }
            int score = CombinationScorer.score(candidate.numbers());
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** 과거 당첨 조합이면 {@value #HISTORY_RETRY_CAP}회까지 다시 뽑는다(HIST-01, HIST-08 우회 금지). */
    private LottoNumbers drawNonHistorical(List<Integer> pool, Set<Integer> locked,
                                            RecommendationHistorySnapshot snapshot, Random random) {
        for (int i = 0; i < HISTORY_RETRY_CAP; i++) {
            LottoNumbers candidate = drawOne(pool, locked, random);
            if (!snapshot.winningMasks().contains(candidate.mask())) {
                return candidate;
            }
        }
        return null;
    }

    private LottoNumbers drawOne(List<Integer> pool, Set<Integer> locked, Random random) {
        List<Integer> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        int need = LottoNumbers.SIZE - locked.size();
        List<Integer> picked = new ArrayList<>(locked);
        picked.addAll(shuffled.subList(0, need));
        return LottoNumbers.of(picked);
    }

    private List<Integer> freePool(NormalizedRecommendationRequest request) {
        List<Integer> pool = new ArrayList<>();
        for (int n = LottoNumbers.MIN; n <= LottoNumbers.MAX; n++) {
            if (!request.lockedNumbers().contains(n) && !request.excludedNumbers().contains(n)) {
                pool.add(n);
            }
        }
        return pool;
    }
}
