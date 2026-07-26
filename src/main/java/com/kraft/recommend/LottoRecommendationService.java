package com.kraft.recommend;

import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoNumberCodec;
import com.kraft.winningnumber.WinningBallsOnly;
import com.kraft.winningnumber.WinningNumberRepository;
import com.kraft.winningnumber.WinningNumbersCollectedEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class LottoRecommendationService {

    private static final int MAX_ATTEMPTS = 100;
    private static final int PRIZE_CANDIDATE_POOL = 50;
    static final String STRATEGY_REDUCE_SHARED_WINNER_RISK = "reduce_shared_winner_risk";
    static final String STRATEGY_RANDOM = "random";
    private static final String RANDOM_ALGORITHM_VERSION = "uniform-random-v1";

    private final LottoNumberCodec lottoNumberCodec;
    private final WinningNumberRepository winningNumberRepository;
    private final CombinationScorer combinationScorer;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Timer recommendTimer;

    // 조합 비트마스크(ball n → bit n-1)와 이력 완전성(회차 수·최신 반영 회차·최초 누락 회차)을
    // 한 번에 갱신되는 원자적 스냅샷으로 묶는다. 별개 필드로 뒀다면 refresh 도중 masks만
    // 갱신되고 metadata는 이전 값인 순간이 생겨(비원자적 갱신), ready 판정이 masks 상태와
    // 어긋날 수 있다.
    // NOTE: 인스턴스별 상태다 — DB가 단일 진실 공급원이라 인스턴스 수가 늘어나도 정합성
    // 문제는 없지만, 각 인스턴스가 독립적으로 리빌드하므로 롤링 배포로 막 뜬 인스턴스는
    // 리빌드가 끝나기 전까지 ready()==false라 추천 요청을 fail-closed로 거부한다(요청
    // 실패지 오답이 아니다). 헬스체크가 이 gauge(kraft_lotto_history_ready)를 트래픽
    // 유입 조건으로 삼지 않으면 롤링 배포 중 일시적 503이 노출될 수 있다.
    private volatile HistorySnapshot historySnapshot = HistorySnapshot.empty();

    record HistorySnapshot(Set<Long> masks, int roundCount, int historyThroughRound,
                            Integer firstMissingRound, Instant loadedAt) {
        static HistorySnapshot empty() {
            return new HistorySnapshot(Set.of(), 0, 0, null, Instant.EPOCH);
        }

        /**
         * 1회부터 최신 회차까지 빈틈없이 로드돼야 배제 이력을 신뢰할 수 있다(P1-05) — 중간
         * 누락 회차가 있으면 그 회차의 1등 조합이 배제 목록에서 빠져 있을 수 있으므로, DB가
         * 비어 있는 경우(roundCount=0)뿐 아니라 연속성이 깨진 경우(firstMissingRound!=null)도
         * fail-closed로 막는다. 테스트 픽스처(BaseApiIntegrationTest 등)는 이 정책에 맞춰
         * 회차를 1부터 연속으로 시딩해야 한다.
         */
        boolean ready() {
            return roundCount > 0 && firstMissingRound == null;
        }
    }

    // 부분 Fisher-Yates 셔플의 난수 소스. 기본은 ThreadLocalRandom이지만, 경계 시나리오
    // (충돌 상한 도달 등)를 확률에 기대지 않고 결정론적으로 재현하려면 테스트에서
    // setRandomSource()로 고정된 시퀀스를 주입한다(패키지 프라이빗 — 같은 패키지 테스트 전용).
    private IntUnaryOperator randomSource = ThreadLocalRandom.current()::nextInt;

    void setRandomSource(IntUnaryOperator randomSource) {
        this.randomSource = randomSource;
    }

    public LottoRecommendationService(LottoNumberCodec lottoNumberCodec,
                                      WinningNumberRepository winningNumberRepository,
                                      CombinationScorer combinationScorer,
                                      Clock clock,
                                      MeterRegistry meterRegistry) {
        this.lottoNumberCodec = lottoNumberCodec;
        this.winningNumberRepository = winningNumberRepository;
        this.combinationScorer = combinationScorer;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.recommendTimer = Timer.builder("kraft_lotto_recommend_duration_seconds")
                .description("추천 생성 1회 처리 시간(검증·재추첨 포함)")
                .register(meterRegistry);

        Gauge.builder("kraft_lotto_history_ready", this, s -> s.historySnapshot.ready() ? 1d : 0d)
                .description("추천 이력이 1회부터 최신 회차까지 빈틈없이 로드되어 배제 보장이 유효한지(1=준비됨)")
                .register(meterRegistry);
        Gauge.builder("kraft_lotto_history_data_present", this,
                        s -> s.historySnapshot.roundCount() > 0 ? 1d : 0d)
                .description("추천 이력 DB에 회차 데이터가 하나라도 있는지(1=있음, roundCount>0 여부만 반영, "
                        + "연속성은 무관 — ready=0이 '완전히 빔'인지 '중간 누락'인지 구분하는 용도)")
                .register(meterRegistry);
        Gauge.builder("kraft_lotto_history_round_count", this, s -> (double) s.historySnapshot.roundCount())
                .description("추천 배제 이력에 반영된 회차 수")
                .register(meterRegistry);
        Gauge.builder("kraft_lotto_first_missing_round", this,
                        s -> (double) (s.historySnapshot.firstMissingRound() == null
                                ? 0 : s.historySnapshot.firstMissingRound()))
                .description("1회부터 최신 회차까지 중 최초로 누락된 회차(0=누락 없음)")
                .register(meterRegistry);
    }

    @PostConstruct
    void loadHistoricalCombinations() {
        refreshHistoricalCombinations();
    }

    /**
     * 운영에서는 수집 완료 이벤트로 캐시가 자동 갱신되지만, 저장소를 직접 시딩하는 테스트
     * 픽스처(BaseApiIntegrationTest 등)는 이벤트를 발행하지 않는다. 그런 경우 시딩 직후
     * 이 메서드로 캐시를 수동 동기화해야 fail-closed(R2)가 오탐하지 않는다.
     */
    public void refreshHistoryCache() {
        refreshHistoricalCombinations();
    }

    /**
     * 커밋 전 동기 리스너(@EventListener)는 트랜잭션이 롤백돼도 이미 메모리 캐시를
     * 갱신해버려 유령 데이터를 남긴다. AFTER_COMMIT으로 전환해 실제 반영된 데이터만 반영한다.
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCollected(WinningNumbersCollectedEvent event) {
        if (event.dataChanged()) {
            refreshHistoricalCombinations();
        }
    }

    private void refreshHistoricalCombinations() {
        List<WinningBallsOnly> all = winningNumberRepository.findAllBalls();
        Set<Long> combos = new HashSet<>();
        List<Integer> rounds = new ArrayList<>(all.size());
        for (WinningBallsOnly wn : all) {
            combos.add(bitmaskOf(wn.getN1(), wn.getN2(), wn.getN3(), wn.getN4(), wn.getN5(), wn.getN6()));
            rounds.add(wn.getRound());
        }
        rounds.sort(Integer::compareTo);

        Integer firstMissingRound = null;
        int expected = 1;
        for (int round : rounds) {
            if (round != expected) {
                firstMissingRound = expected;
                break;
            }
            expected++;
        }
        int historyThroughRound = rounds.isEmpty() ? 0 : rounds.get(rounds.size() - 1);

        historySnapshot = new HistorySnapshot(
                Set.copyOf(combos), rounds.size(), historyThroughRound, firstMissingRound, Instant.now(clock));
    }

    public boolean isHistoricalFirstPrizeCombination(List<Integer> numbers) {
        List<Integer> normalized = lottoNumberCodec.normalize(numbers);
        return historySnapshot.masks().contains(bitmaskOf(normalized));
    }

    /** 정렬 여부와 무관하게 번호 6개(1~45)를 45비트 이내의 long으로 인코딩한다(ball n → bit n-1). */
    private static long bitmaskOf(int n1, int n2, int n3, int n4, int n5, int n6) {
        return (1L << (n1 - 1)) | (1L << (n2 - 1)) | (1L << (n3 - 1))
                | (1L << (n4 - 1)) | (1L << (n5 - 1)) | (1L << (n6 - 1));
    }

    private static long bitmaskOf(List<Integer> numbers) {
        long mask = 0L;
        for (int n : numbers) {
            mask |= 1L << (n - 1);
        }
        return mask;
    }

    private static long excludedMaskOf(Set<Integer> excluded) {
        long mask = 0L;
        for (int n : excluded) {
            mask |= 1L << (n - 1);
        }
        return mask;
    }

    public RecommendNumbersResponse recommend(RecommendNumbersRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return doRecommend(request);
        } finally {
            sample.stop(recommendTimer);
        }
    }

    // B-08: 검증·메트릭·조합·샘플링이 한 메서드에 섞여 있던 것을, 로직은 그대로 두고
    // "요청 해석 → 실행 가능성 검증 → 샘플링 → 응답 조립" 네 단계로만 나눈다(동작 동일
    // 보장은 기존 LottoRecommendationServiceTest의 속성 기반 테스트가 검증).
    private record RequestContext(int count, boolean reduceSharedWinnerRisk, String strategy, Set<Integer> excluded) {}

    private RecommendNumbersResponse doRecommend(RecommendNumbersRequest request) {
        HistorySnapshot snapshot = historySnapshot;
        if (!snapshot.ready()) {
            throw fail(HttpStatus.SERVICE_UNAVAILABLE, "RECOMMENDATION_HISTORY_NOT_READY",
                    "역대 1등 배제 이력이 아직 준비되지 않았습니다(회차 수 %d, 최초 누락 회차 %s)."
                            .formatted(snapshot.roundCount(), snapshot.firstMissingRound()));
        }

        RequestContext ctx = parseRequest(request);
        meterRegistry.counter("kraft_lotto_recommend_requests_total", "strategy", ctx.strategy()).increment();
        validateFeasibility(ctx, snapshot);

        List<List<Integer>> recommendations = sampleRecommendations(ctx, snapshot);

        String algorithmVersion = ctx.reduceSharedWinnerRisk() ? CombinationScorer.VERSION : RANDOM_ALGORITHM_VERSION;
        return new RecommendNumbersResponse(recommendations, ctx.strategy(), algorithmVersion, snapshot.historyThroughRound());
    }

    private RequestContext parseRequest(RecommendNumbersRequest request) {
        int count = request == null || request.count() == null ? 1 : request.count();
        boolean reduceSharedWinnerRisk = request != null && Boolean.TRUE.equals(request.reduceSharedWinnerRisk());
        String strategy = reduceSharedWinnerRisk ? STRATEGY_REDUCE_SHARED_WINNER_RISK : STRATEGY_RANDOM;
        Set<Integer> excluded = request == null || request.excludedNumbers() == null
                ? Set.of()
                : new HashSet<>(lottoNumberCodec.normalizeSubset(request.excludedNumbers()));
        return new RequestContext(count, reduceSharedWinnerRisk, strategy, excluded);
    }

    private void validateFeasibility(RequestContext ctx, HistorySnapshot snapshot) {
        if (45 - ctx.excluded().size() < 6) {
            throw fail(HttpStatus.BAD_REQUEST, "TOO_MANY_EXCLUSIONS",
                    "제외 번호를 적용한 뒤에도 최소 6개 번호가 남아야 합니다.");
        }

        long available = 45L - ctx.excluded().size();
        long possible = combinations(available, 6);
        long excludedMask = excludedMaskOf(ctx.excluded());
        long compatibleHistoricalCount = snapshot.masks().stream()
                .filter(mask -> (mask & excludedMask) == 0L)
                .count();
        long allowedPossible = possible - compatibleHistoricalCount;
        if (ctx.count() > allowedPossible) {
            throw fail(HttpStatus.BAD_REQUEST, "INSUFFICIENT_UNIQUE_COMBINATIONS",
                    "요청한 조합 수(" + ctx.count() + ")가 역대 1등 조합을 제외하고 가능한 고유 조합 수("
                            + allowedPossible + ")를 초과합니다.");
        }
    }

    private List<List<Integer>> sampleRecommendations(RequestContext ctx, HistorySnapshot snapshot) {
        List<Integer> candidates = buildCandidates(ctx.excluded());
        List<List<Integer>> recommendations = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int attempts = 0;
        int maxAttempts = ctx.count() * MAX_ATTEMPTS;
        Set<Long> historicalMasks = snapshot.masks();
        while (recommendations.size() < ctx.count() && attempts++ < maxAttempts) {
            List<Integer> candidate = ctx.reduceSharedWinnerRisk()
                    ? generateBest(candidates, historicalMasks)
                    : generateOne(candidates, historicalMasks);
            if (candidate != null && seen.add(bitmaskOf(candidate))) {
                recommendations.add(candidate);
            }
        }
        // 이론상 가능한 조합 수(allowedPossible) 안에서도, 역대 당첨 조합 회피와 중복 회피가
        // 겹쳐 maxAttempts 안에 count만큼 못 채울 수 있다 — 부족분을 조용히 반환하지 않고 명시한다.
        if (recommendations.size() < ctx.count()) {
            throw fail(HttpStatus.BAD_REQUEST, "INSUFFICIENT_UNIQUE_COMBINATIONS",
                    "생성 가능한 고유 조합이 부족합니다(생성 %d / 요청 %d)."
                            .formatted(recommendations.size(), ctx.count()));
        }
        return recommendations;
    }

    private ApiException fail(HttpStatus status, String code, String message) {
        meterRegistry.counter("kraft_lotto_recommend_failures_total", "code", code).increment();
        return new ApiException(status, code, message);
    }

    /**
     * 후보 풀에서 비인기도 점수가 가장 높은 조합을 반환한다.
     * 공동 당첨자를 최소화해 개인 수령액을 최대화하는 목적.
     * generateOne이 충돌 상한 도달로 null을 반환하면(과거 1등과의 충돌을 해소하지 못함)
     * 그 시도는 후보 풀에서 제외한다 — 점수 비교 대상에 과거 1등 조합이 섞이면 안 된다.
     */
    private List<Integer> generateBest(List<Integer> candidates, Set<Long> historicalMasks) {
        List<Integer> best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < PRIZE_CANDIDATE_POOL; i++) {
            List<Integer> candidate = generateOne(candidates, historicalMasks);
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

    private static long combinations(long n, int k) {
        long result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    private static List<Integer> buildCandidates(Set<Integer> excluded) {
        List<Integer> candidates = new ArrayList<>(45 - excluded.size());
        for (int i = 1; i <= 45; i++) {
            if (!excluded.contains(i)) {
                candidates.add(i);
            }
        }
        return candidates;
    }

    // 부분 Fisher-Yates(k=6): 전체 ~45개 대신 앞 6개 위치만 셔플해 O(n) → O(k) 로 단축.
    // candidates 배열을 호출 간 재사용하므로 요청당 한 번만 빌드한다.
    // MAX_ATTEMPTS 안에 과거 1등과 겹치지 않는 조합을 못 찾으면, 마지막 셔플 결과를
    // 그대로 반환하지 않고 null을 돌려준다 — 과거 1등 조합이 추천으로 새어나가지 않도록
    // 호출자(recommend())가 이 시도를 버리고 재시도하거나 최종적으로 명시적 오류를 낸다.
    private List<Integer> generateOne(List<Integer> candidates, Set<Long> historicalMasks) {
        int n = candidates.size();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            for (int i = 0; i < 6; i++) {
                int j = i + randomSource.applyAsInt(n - i);
                int tmp = candidates.get(i);
                candidates.set(i, candidates.get(j));
                candidates.set(j, tmp);
            }
            List<Integer> result = lottoNumberCodec.normalize(candidates.subList(0, 6));
            if (!historicalMasks.contains(bitmaskOf(result))) {
                return result;
            }
            meterRegistry.counter("kraft_lotto_recommend_historical_collisions_total").increment();
        }
        return null;
    }
}
