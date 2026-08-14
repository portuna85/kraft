package com.kraft.recommend;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoBitmask;
import com.kraft.common.lotto.LottoNumberCodec;
import com.kraft.winningnumber.WinningBallsOnly;
import com.kraft.winningnumber.WinningNumberRepository;
import com.kraft.winningnumber.WinningNumbersCollectedEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class LottoRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(LottoRecommendationService.class);

    // L-02(improvement_codex.md): historyStatus()의 DB 버전 조회가 실패하면 이전에는
    // 매 스크레이프(15초)마다 스택트레이스 전체를 로그로 남겼다 — DB 장애 중 로그
    // 폭주로 장애를 증폭시킨다. RedisRateLimitCounter와 같은 스로틀 상수·간격을 쓴다.
    private static final long HISTORY_STATUS_FAILURE_LOG_THROTTLE_MILLIS = 30_000L;
    private static final int MAX_ATTEMPTS = 100;
    private static final int PRIZE_CANDIDATE_POOL = 50;
    private static final int BALANCED_CANDIDATE_POOL = 50;
    private static final int MAX_LOCKED_NUMBERS = 5;
    static final String STRATEGY_REDUCE_SHARED_WINNER_RISK = "reduce_shared_winner_risk";
    static final String STRATEGY_RANDOM = "random";
    static final String STRATEGY_BALANCED = "balanced";
    private static final String RANDOM_ALGORITHM_VERSION = "uniform-random-v1";
    public static final String EXCLUSION_POLICY_VERSION = "historical-first-prize-v1";

    private final LottoNumberCodec lottoNumberCodec;
    private final WinningNumberRepository winningNumberRepository;
    private final CombinationScorer combinationScorer;
    private final BalancedScorer balancedScorer;
    private final RecommendationSetHistoryService recommendationSetHistoryService;
    private final RecommendationHistoryStateRepository historyStateRepository;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Timer recommendTimer;
    private final Timer historyRefreshTimer;

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

    private final AtomicLong lastHistoryStatusFailureLogMillis = new AtomicLong(0);

    record HistorySnapshot(Set<Long> masks, int roundCount, int historyThroughRound,
                            Integer firstMissingRound, long version, Instant loadedAt) {
        static HistorySnapshot empty() {
            return new HistorySnapshot(Set.of(), 0, 0, null, 0L, Instant.EPOCH);
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

    /** readiness와 운영 지표가 사용하는 이력 상태의 읽기 전용 표현. */
    public record HistoryStatus(boolean ready, long snapshotVersion, long databaseVersion,
                                int roundCount, int historyThroughRound, Integer firstMissingRound) {
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
                                      BalancedScorer balancedScorer,
                                      RecommendationSetHistoryService recommendationSetHistoryService,
                                      RecommendationHistoryStateRepository historyStateRepository,
                                      Clock clock,
                                      MeterRegistry meterRegistry) {
        this.lottoNumberCodec = lottoNumberCodec;
        this.winningNumberRepository = winningNumberRepository;
        this.combinationScorer = combinationScorer;
        this.balancedScorer = balancedScorer;
        this.recommendationSetHistoryService = recommendationSetHistoryService;
        this.historyStateRepository = historyStateRepository;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.recommendTimer = Timer.builder("kraft_lotto_recommend_duration_seconds")
                .description("추천 생성 1회 처리 시간(검증·재추첨 포함)")
                .register(meterRegistry);
        this.historyRefreshTimer = Timer.builder("kraft_lotto_history_refresh_duration_seconds")
                .description("Time spent loading and validating the recommendation history snapshot")
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
        Gauge.builder("kraft_lotto_history_snapshot_version", this, s -> (double) s.historySnapshot.version())
                .description("현재 인스턴스 추천 이력 스냅샷 버전")
                .register(meterRegistry);
        Gauge.builder("kraft_lotto_history_db_version", this,
                        s -> (double) Math.max(-1L, s.historyStatus().databaseVersion()))
                .description("Current recommendation-history version read from the database; -1 means unavailable")
                .register(meterRegistry);
        Gauge.builder("kraft_lotto_history_snapshot_age_seconds", this,
                        s -> Duration.between(s.historySnapshot.loadedAt(), Instant.now(s.clock)).toSeconds())
                .description("Age of the in-memory recommendation-history snapshot")
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
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
        long versionBeforeLoad = currentHistoryVersion();
        List<WinningBallsOnly> all = winningNumberRepository.findAllBalls();
        Set<Long> combos = new HashSet<>();
        List<Integer> rounds = new ArrayList<>(all.size());
        for (WinningBallsOnly wn : all) {
            combos.add(LottoBitmask.of(wn.getN1(), wn.getN2(), wn.getN3(), wn.getN4(), wn.getN5(), wn.getN6()));
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
                Set.copyOf(combos), rounds.size(), historyThroughRound, firstMissingRound,
                versionBeforeLoad, Instant.now(clock));
        } finally {
            sample.stop(historyRefreshTimer);
        }
    }

    /** DB 버전과 스냅샷 버전을 함께 확인한다. DB 장애도 안전하게 추천 중단으로 처리한다. */
    public HistoryStatus historyStatus() {
        HistorySnapshot snapshot = historySnapshot;
        long databaseVersion;
        try {
            databaseVersion = currentHistoryVersion();
        } catch (RuntimeException e) {
            // H-6: DB 장애와 정상적인 스냅샷 미준비가 로그상 구분되지 않았다 — 동작은
            // fail-closed(추천 중단, databaseVersion=-1)로 안전하게 유지하되, 조사 가능하게
            // 원인을 남긴다. 이 메서드는 스크레이프마다 호출되므로(gauge) 장애 지속 중에는
            // L-02에 따라 로그를 스로틀한다 — fail-closed 동작 자체는 매번 그대로 적용된다.
            logHistoryStatusFailureThrottled(e);
            return new HistoryStatus(false, snapshot.version(), -1L, snapshot.roundCount(),
                    snapshot.historyThroughRound(), snapshot.firstMissingRound());
        }
        return new HistoryStatus(snapshot.ready() && snapshot.version() == databaseVersion,
                snapshot.version(), databaseVersion, snapshot.roundCount(), snapshot.historyThroughRound(),
                snapshot.firstMissingRound());
    }

    Instant historyLoadedAt() {
        return historySnapshot.loadedAt();
    }

    private void logHistoryStatusFailureThrottled(RuntimeException cause) {
        long now = System.currentTimeMillis();
        long last = lastHistoryStatusFailureLogMillis.get();
        if (now - last >= HISTORY_STATUS_FAILURE_LOG_THROTTLE_MILLIS
                && lastHistoryStatusFailureLogMillis.compareAndSet(last, now)) {
            log.warn("추천 이력 버전 조회 실패 — fail-closed로 추천을 중단한다. 이 경고는 {}ms 간격으로 스로틀된다.",
                    HISTORY_STATUS_FAILURE_LOG_THROTTLE_MILLIS, cause);
        }
    }

    private long currentHistoryVersion() {
        return historyStateRepository.findById(1)
                .map(RecommendationHistoryState::getVersion)
                .orElse(0L);
    }

    public boolean isHistoricalFirstPrizeCombination(List<Integer> numbers) {
        List<Integer> normalized = lottoNumberCodec.normalize(numbers);
        return historySnapshot.masks().contains(LottoBitmask.of(normalized));
    }


    /**
     * clientTokenHash가 있으면(X-Device-Token 헤더 제공) 결과를 recommendation_sets/items에
     * 영속화하고 응답에 setId/items/createdAt을 채운다. 없으면(기존 호환 클라이언트) 영속화를
     * 건너뛰고 그 필드들은 null로 남긴다 — recommendation_sets는 소유권 없는 행을 허용하지
     * 않으므로 익명 이력을 남기지 않을 요청까지 강제로 소유자 없는 행을 만들지 않는다.
     */
    public RecommendNumbersResponse recommend(RecommendNumbersRequest request, String clientTokenHash) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return doRecommend(request, clientTokenHash);
        } finally {
            sample.stop(recommendTimer);
        }
    }

    // B-08: 검증·메트릭·조합·샘플링이 한 메서드에 섞여 있던 것을, 로직은 그대로 두고
    // "요청 해석 → 실행 가능성 검증 → 샘플링 → 응답 조립" 네 단계로만 나눈다(동작 동일
    // 보장은 기존 LottoRecommendationServiceTest의 속성 기반 테스트가 검증).
    private record RequestContext(int count, String strategy, List<Integer> locked, Set<Integer> excluded) {}

    private RecommendNumbersResponse doRecommend(RecommendNumbersRequest request, String clientTokenHash) {
        HistorySnapshot snapshot = historySnapshot;
        HistoryStatus status = historyStatus();
        if (!status.ready()) {
            meterRegistry.counter("kraft_recommendation_rejected_total", "reason", "history_not_ready").increment();
            // 다른 인스턴스에서 새 회차를 반영한 경우에도 다음 요청에는 최신 스냅샷을 쓸 수 있게
            // 현재 요청을 성공시키지 않은 채 동기 재구축한다. 이 요청은 항상 fail-closed다.
            if (status.databaseVersion() >= 0 && snapshot.version() != status.databaseVersion()) {
                refreshHistoricalCombinations();
            }
            throw fail(ApiErrorCode.RECOMMENDATION_HISTORY_NOT_READY,
                    "역대 1등 배제 이력이 아직 준비되지 않았습니다(스냅샷 버전 %d, DB 버전 %d, 회차 수 %d, 최초 누락 회차 %s)."
                            .formatted(status.snapshotVersion(), status.databaseVersion(), status.roundCount(),
                                    status.firstMissingRound()));
        }

        RequestContext ctx = parseRequest(request);
        meterRegistry.counter("kraft_lotto_recommend_requests_total", "strategy", ctx.strategy()).increment();
        validateFeasibility(ctx, snapshot);

        List<RecommendationItemView> items;
        Timer.Sample strategySample = Timer.start(meterRegistry);
        try {
            items = sampleRecommendations(ctx, snapshot);
        } finally {
            strategySample.stop(Timer.builder("kraft_lotto_recommend_strategy_duration_seconds")
                    .description("추천 모드별 샘플링 처리 시간")
                    .tag("strategy", ctx.strategy())
                    .register(meterRegistry));
        }
        // 샘플링 중 새 당첨 회차가 커밋됐으면 생성 시점의 이력이 더 최신이므로, 이 응답을
        // 성공시키지 않는다. 이 검사는 추천의 선형화 지점을 응답 조립 직전으로 만든다.
        HistoryStatus statusAfterSampling = historyStatus();
        if (!statusAfterSampling.ready() || snapshot.version() != statusAfterSampling.databaseVersion()) {
            meterRegistry.counter("kraft_recommendation_rejected_total", "reason", "history_changed").increment();
            if (statusAfterSampling.databaseVersion() >= 0
                    && historySnapshot.version() != statusAfterSampling.databaseVersion()) {
                refreshHistoricalCombinations();
            }
            throw fail(ApiErrorCode.RECOMMENDATION_HISTORY_NOT_READY,
                    "추천 중 당첨 이력 버전이 변경되어 다시 확인해야 합니다.");
        }
        meterRegistry.counter("kraft_lotto_recommend_success_total", "strategy", ctx.strategy()).increment();
        List<List<Integer>> recommendations = items.stream().map(RecommendationItemView::numbers).toList();

        String algorithmVersion = algorithmVersionOf(ctx.strategy());

        Long setId = null;
        OffsetDateTime createdAt = null;
        if (clientTokenHash != null) {
            createdAt = OffsetDateTime.now(clock);
            setId = recommendationSetHistoryService.persist(clientTokenHash, ctx.strategy(), algorithmVersion,
                    snapshot.historyThroughRound(), EXCLUSION_POLICY_VERSION,
                    ctx.locked(), ctx.excluded().stream().sorted().toList(),
                    items, createdAt);
        }

        return new RecommendNumbersResponse(
                recommendations, RecommendationStrategy.fromWire(ctx.strategy()), algorithmVersion,
                snapshot.historyThroughRound(),
                true, EXCLUSION_POLICY_VERSION,
                setId, items, createdAt);
    }

    private static String algorithmVersionOf(String strategy) {
        return switch (strategy) {
            case STRATEGY_REDUCE_SHARED_WINNER_RISK -> CombinationScorer.VERSION;
            case STRATEGY_BALANCED -> BalancedScorer.VERSION;
            default -> RANDOM_ALGORITHM_VERSION;
        };
    }

    private RequestContext parseRequest(RecommendNumbersRequest request) {
        int count = request == null || request.count() == null ? 1 : request.count();

        List<Integer> locked = request == null || request.lockedNumbers() == null
                ? List.of()
                : lottoNumberCodec.normalizeSubset(request.lockedNumbers());
        if (locked.size() > MAX_LOCKED_NUMBERS) {
            throw fail(ApiErrorCode.TOO_MANY_LOCKED_NUMBERS,
                    "고정 번호는 최대 " + MAX_LOCKED_NUMBERS + "개까지 지정할 수 있습니다.");
        }

        Set<Integer> excluded = request == null || request.excludedNumbers() == null
                ? Set.of()
                : new HashSet<>(lottoNumberCodec.normalizeSubset(request.excludedNumbers()));

        if (!Collections.disjoint(locked, excluded)) {
            throw fail(ApiErrorCode.LOCKED_EXCLUDED_CONFLICT,
                    "고정 번호와 제외 번호가 겹칠 수 없습니다.");
        }

        String strategy = resolveStrategy(request);
        return new RequestContext(count, strategy, locked, excluded);
    }

    private String resolveStrategy(RecommendNumbersRequest request) {
        if (request != null && request.maximizePrize() != null) {
            // 구 필드명(maximizePrize) 사용량 추적 — 이 값이 계속 0에 머물면
            // 별도 변경에서 필드 자체를 제거해도 안전하다고 판단할 근거가 된다.
            meterRegistry.counter("kraft_recommend_legacy_field_used_total").increment();
        }
        String strategyParam = request == null ? null : request.strategy();
        if (strategyParam != null && !strategyParam.isBlank()) {
            String normalized = strategyParam.trim().toLowerCase();
            return switch (normalized) {
                case STRATEGY_RANDOM, STRATEGY_BALANCED, STRATEGY_REDUCE_SHARED_WINNER_RISK -> normalized;
                default -> throw fail(ApiErrorCode.INVALID_RECOMMENDATION_STRATEGY,
                        "지원하지 않는 추천 전략입니다: " + strategyParam);
            };
        }
        boolean reduceSharedWinnerRisk = request != null
                && Boolean.TRUE.equals(request.effectiveReduceSharedWinnerRisk());
        return reduceSharedWinnerRisk ? STRATEGY_REDUCE_SHARED_WINNER_RISK : STRATEGY_RANDOM;
    }

    private void validateFeasibility(RequestContext ctx, HistorySnapshot snapshot) {
        if (45 - ctx.excluded().size() < 6) {
            throw fail(ApiErrorCode.TOO_MANY_EXCLUSIONS,
                    "제외 번호를 적용한 뒤에도 최소 6개 번호가 남아야 합니다.");
        }

        long available = 45L - ctx.excluded().size() - ctx.locked().size();
        int need = 6 - ctx.locked().size();
        long possible = combinations(available, need);
        long excludedMask = LottoBitmask.of(ctx.excluded());
        long lockedMask = LottoBitmask.of(ctx.locked());
        long compatibleHistoricalCount = snapshot.masks().stream()
                .filter(mask -> (mask & excludedMask) == 0L && (mask & lockedMask) == lockedMask)
                .count();
        long allowedPossible = possible - compatibleHistoricalCount;
        if (ctx.count() > allowedPossible) {
            throw fail(ApiErrorCode.INSUFFICIENT_UNIQUE_COMBINATIONS,
                    "요청한 조합 수(" + ctx.count() + ")가 역대 1등 조합을 제외하고 가능한 고유 조합 수("
                            + allowedPossible + ")를 초과합니다.");
        }
    }

    private List<RecommendationItemView> sampleRecommendations(RequestContext ctx, HistorySnapshot snapshot) {
        return switch (ctx.strategy()) {
            case STRATEGY_BALANCED -> sampleBalanced(ctx, snapshot);
            case STRATEGY_REDUCE_SHARED_WINNER_RISK -> sampleSimple(ctx, snapshot, true);
            default -> sampleSimple(ctx, snapshot, false);
        };
    }

    private List<RecommendationItemView> sampleSimple(RequestContext ctx, HistorySnapshot snapshot, boolean reduceSharedWinnerRisk) {
        List<Integer> candidates = buildCandidates(ctx.excluded(), ctx.locked());
        List<List<Integer>> recommendations = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int attempts = 0;
        int maxAttempts = ctx.count() * MAX_ATTEMPTS;
        Set<Long> historicalMasks = snapshot.masks();
        while (recommendations.size() < ctx.count() && attempts++ < maxAttempts) {
            List<Integer> candidate = reduceSharedWinnerRisk
                    ? generateBest(candidates, historicalMasks, ctx.locked())
                    : generateOne(candidates, historicalMasks, ctx.locked());
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
    private List<RecommendationItemView> sampleBalanced(RequestContext ctx, HistorySnapshot snapshot) {
        List<Integer> candidates = buildCandidates(ctx.excluded(), ctx.locked());
        Set<Long> historicalMasks = snapshot.masks();
        Set<Long> seen = new HashSet<>();
        List<List<Integer>> pool = new ArrayList<>();
        int poolTarget = Math.max(BALANCED_CANDIDATE_POOL, ctx.count() * 10);
        int attempts = 0;
        int maxAttempts = poolTarget * MAX_ATTEMPTS;
        while (pool.size() < poolTarget && attempts++ < maxAttempts) {
            List<Integer> candidate = generateOne(candidates, historicalMasks, ctx.locked());
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

    private ApiException fail(ApiErrorCode errorCode, String message) {
        meterRegistry.counter("kraft_lotto_recommend_failures_total", "code", errorCode.name()).increment();
        return new ApiException(errorCode, message);
    }

    /**
     * 후보 풀에서 비인기도 점수가 가장 높은 조합을 반환한다.
     * 공동 당첨자를 최소화해 개인 수령액을 최대화하는 목적.
     * generateOne이 충돌 상한 도달로 null을 반환하면(과거 1등과의 충돌을 해소하지 못함)
     * 그 시도는 후보 풀에서 제외한다 — 점수 비교 대상에 과거 1등 조합이 섞이면 안 된다.
     */
    private List<Integer> generateBest(List<Integer> candidates, Set<Long> historicalMasks, List<Integer> locked) {
        List<Integer> best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < PRIZE_CANDIDATE_POOL; i++) {
            List<Integer> candidate = generateOne(candidates, historicalMasks, locked);
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
        if (n < k) {
            return 0;
        }
        long result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
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
    private List<Integer> generateOne(List<Integer> candidates, Set<Long> historicalMasks, List<Integer> locked) {
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
}
