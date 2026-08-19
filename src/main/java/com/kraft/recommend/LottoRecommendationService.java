package com.kraft.recommend;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoBitmask;
import com.kraft.common.lotto.LottoNumberCodec;
import com.kraft.winningnumber.WinningNumbersCollectedEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class LottoRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(LottoRecommendationService.class);

    // L-02: historyStatus()의 DB 버전 조회가 실패하면 이전에는
    // 매 스크레이프(15초)마다 스택트레이스 전체를 로그로 남겼다 — DB 장애 중 로그
    // 폭주로 장애를 증폭시킨다. RedisRateLimitCounter와 같은 스로틀 상수·간격을 쓴다.
    private static final long HISTORY_STATUS_FAILURE_LOG_THROTTLE_MILLIS = 30_000L;
    static final String STRATEGY_REDUCE_SHARED_WINNER_RISK = "reduce_shared_winner_risk";
    static final String STRATEGY_RANDOM = "random";
    static final String STRATEGY_BALANCED = "balanced";
    private static final String RANDOM_ALGORITHM_VERSION = "uniform-random-v1";
    public static final String EXCLUSION_POLICY_VERSION = "historical-first-prize-v1";

    private final LottoNumberCodec lottoNumberCodec;
    private final RecommendationSetHistoryService recommendationSetHistoryService;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Timer recommendTimer;
    // TD-022: 요청마다(doRecommend) Timer.builder(...)를 새로 만들어 레지스트리를 조회하는
    // 대신, 전략 문자열이 3가지로 고정돼 있으므로 생성자에서 한 번씩만 등록해 둔다.
    private final Map<String, Timer> strategyDurationTimers;

    // TD-008 1~3단계: 이력 스냅샷 수명주기, 요청 파싱/실현가능성 검증, 전략 디스패치·후보
    // 생성을 각각 RecommendationHistorySnapshotManager/RecommendationRequestValidator/
    // RecommendationCandidateGenerator로 옮겼다. REF-01: 셋 다 @Component로 등록해 생성자
    // 주입으로 받는다 — 테스트의 다중 인스턴스 staleness 시나리오는 이 세 협력자도 함께
    // new로 만들어 생성자에 넘기면 되므로 DI 전환과 무관하게 계속 동작한다.
    private final RecommendationHistorySnapshotManager historySnapshotManager;
    private final RecommendationRequestValidator requestValidator;
    private final RecommendationCandidateGenerator candidateGenerator;

    private final AtomicLong lastHistoryStatusFailureLogMillis = new AtomicLong(0);

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
                                      RecommendationSetHistoryService recommendationSetHistoryService,
                                      RecommendationHistorySnapshotManager historySnapshotManager,
                                      RecommendationRequestValidator requestValidator,
                                      RecommendationCandidateGenerator candidateGenerator,
                                      Clock clock,
                                      MeterRegistry meterRegistry) {
        this.lottoNumberCodec = lottoNumberCodec;
        this.recommendationSetHistoryService = recommendationSetHistoryService;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.historySnapshotManager = historySnapshotManager;
        this.requestValidator = requestValidator;
        this.candidateGenerator = candidateGenerator;
        this.recommendTimer = Timer.builder("kraft_lotto_recommend_duration_seconds")
                .description("추천 생성 1회 처리 시간(검증·재추첨 포함)")
                .register(meterRegistry);
        this.strategyDurationTimers = Stream.of(STRATEGY_RANDOM, STRATEGY_BALANCED, STRATEGY_REDUCE_SHARED_WINNER_RISK)
                .collect(Collectors.toMap(strategy -> strategy, strategy -> Timer.builder(
                                "kraft_lotto_recommend_strategy_duration_seconds")
                        .description("추천 모드별 샘플링 처리 시간")
                        .tag("strategy", strategy)
                        .register(meterRegistry)));

        Gauge.builder("kraft_lotto_history_ready", this,
                        s -> s.historySnapshotManager.currentSnapshot().ready() ? 1d : 0d)
                .description("추천 이력이 1회부터 최신 회차까지 빈틈없이 로드되어 배제 보장이 유효한지(1=준비됨)")
                .register(meterRegistry);
        Gauge.builder("kraft_lotto_history_data_present", this,
                        s -> s.historySnapshotManager.currentSnapshot().roundCount() > 0 ? 1d : 0d)
                .description("추천 이력 DB에 회차 데이터가 하나라도 있는지(1=있음, roundCount>0 여부만 반영, "
                        + "연속성은 무관 — ready=0이 '완전히 빔'인지 '중간 누락'인지 구분하는 용도)")
                .register(meterRegistry);
        Gauge.builder("kraft_lotto_history_round_count", this,
                        s -> (double) s.historySnapshotManager.currentSnapshot().roundCount())
                .description("추천 배제 이력에 반영된 회차 수")
                .register(meterRegistry);
        Gauge.builder("kraft_lotto_first_missing_round", this,
                        s -> (double) (s.historySnapshotManager.currentSnapshot().firstMissingRound() == null
                                ? 0 : s.historySnapshotManager.currentSnapshot().firstMissingRound()))
                .description("1회부터 최신 회차까지 중 최초로 누락된 회차(0=누락 없음)")
                .register(meterRegistry);
        Gauge.builder("kraft_lotto_history_snapshot_version", this,
                        s -> (double) s.historySnapshotManager.currentSnapshot().version())
                .description("현재 인스턴스 추천 이력 스냅샷 버전")
                .register(meterRegistry);
        Gauge.builder("kraft_lotto_history_db_version", this,
                        s -> (double) Math.max(-1L, s.historyStatus().databaseVersion()))
                .description("Current recommendation-history version read from the database; -1 means unavailable")
                .register(meterRegistry);
        Gauge.builder("kraft_lotto_history_snapshot_age_seconds", this,
                        s -> Duration.between(s.historySnapshotManager.currentSnapshot().loadedAt(),
                                Instant.now(s.clock)).toSeconds())
                .description("Age of the in-memory recommendation-history snapshot")
                .register(meterRegistry);
    }

    @PostConstruct
    void loadHistoricalCombinations() {
        historySnapshotManager.refresh();
    }

    /**
     * 운영에서는 수집 완료 이벤트로 캐시가 자동 갱신되지만, 저장소를 직접 시딩하는 테스트
     * 픽스처(BaseApiIntegrationTest 등)는 이벤트를 발행하지 않는다. 그런 경우 시딩 직후
     * 이 메서드로 캐시를 수동 동기화해야 fail-closed(R2)가 오탐하지 않는다.
     */
    public void refreshHistoryCache() {
        historySnapshotManager.refresh();
    }

    /**
     * 커밋 전 동기 리스너(@EventListener)는 트랜잭션이 롤백돼도 이미 메모리 캐시를
     * 갱신해버려 유령 데이터를 남긴다. AFTER_COMMIT으로 전환해 실제 반영된 데이터만 반영한다.
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCollected(WinningNumbersCollectedEvent event) {
        if (event.dataChanged()) {
            historySnapshotManager.refresh();
        }
    }

    /** DB 버전과 스냅샷 버전을 함께 확인한다. DB 장애도 안전하게 추천 중단으로 처리한다. */
    public HistoryStatus historyStatus() {
        RecommendationHistorySnapshotManager.HistorySnapshot snapshot = historySnapshotManager.currentSnapshot();
        long databaseVersion;
        try {
            databaseVersion = historySnapshotManager.currentDatabaseVersion();
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
        return historySnapshotManager.currentSnapshot().loadedAt();
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

    public boolean isHistoricalFirstPrizeCombination(List<Integer> numbers) {
        List<Integer> normalized = lottoNumberCodec.normalize(numbers);
        return historySnapshotManager.currentSnapshot().masks().contains(LottoBitmask.of(normalized));
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
            return doRecommend(request, clientTokenHash, null);
        } finally {
            sample.stop(recommendTimer);
        }
    }

    /**
     * KF-01(docs/improvement.md): 로그인 계정 소유로 생성·영속화한다.
     * {@code MyLibraryController}(인증된 {@code /api/v1/community/me/**} 체인) 전용 —
     * {@code ownerUserId}는 반드시 서버가 인증 컨텍스트({@code CommunityPrincipal})로
     * 확정한 값이어야 하며, 클라이언트가 보낸 식별자를 그대로 넘기면 안 된다.
     */
    public RecommendNumbersResponse recommendForOwner(RecommendNumbersRequest request, Long ownerUserId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return doRecommend(request, null, ownerUserId);
        } finally {
            sample.stop(recommendTimer);
        }
    }

    private RecommendNumbersResponse doRecommend(RecommendNumbersRequest request, String clientTokenHash,
                                                  Long ownerUserId) {
        RecommendationHistorySnapshotManager.HistorySnapshot snapshot = historySnapshotManager.currentSnapshot();
        HistoryStatus status = historyStatus();
        if (!status.ready()) {
            meterRegistry.counter("kraft_recommendation_rejected_total", "reason", "history_not_ready").increment();
            // 다른 인스턴스에서 새 회차를 반영한 경우에도 다음 요청에는 최신 스냅샷을 쓸 수 있게
            // 현재 요청을 성공시키지 않은 채 동기 재구축한다. 이 요청은 항상 fail-closed다.
            if (status.databaseVersion() >= 0 && snapshot.version() != status.databaseVersion()) {
                historySnapshotManager.refresh();
            }
            throw fail(ApiErrorCode.RECOMMENDATION_HISTORY_NOT_READY,
                    "역대 1등 배제 이력이 아직 준비되지 않았습니다(스냅샷 버전 %d, DB 버전 %d, 회차 수 %d, 최초 누락 회차 %s)."
                            .formatted(status.snapshotVersion(), status.databaseVersion(), status.roundCount(),
                                    status.firstMissingRound()));
        }

        RecommendationRequestValidator.RequestContext ctx = requestValidator.parseRequest(request);
        meterRegistry.counter("kraft_lotto_recommend_requests_total", "strategy", ctx.strategy()).increment();
        requestValidator.validateFeasibility(ctx, snapshot);

        List<RecommendationItemView> items;
        Timer.Sample strategySample = Timer.start(meterRegistry);
        try {
            items = candidateGenerator.sampleRecommendations(ctx, snapshot, randomSource);
        } finally {
            strategySample.stop(strategyDurationTimers.get(ctx.strategy()));
        }
        // 샘플링 중 새 당첨 회차가 커밋됐으면 생성 시점의 이력이 더 최신이므로, 이 응답을
        // 성공시키지 않는다. 이 검사는 추천의 선형화 지점을 응답 조립 직전으로 만든다.
        HistoryStatus statusAfterSampling = historyStatus();
        if (!statusAfterSampling.ready() || snapshot.version() != statusAfterSampling.databaseVersion()) {
            meterRegistry.counter("kraft_recommendation_rejected_total", "reason", "history_changed").increment();
            if (statusAfterSampling.databaseVersion() >= 0
                    && historySnapshotManager.currentSnapshot().version() != statusAfterSampling.databaseVersion()) {
                historySnapshotManager.refresh();
            }
            throw fail(ApiErrorCode.RECOMMENDATION_HISTORY_NOT_READY,
                    "추천 중 당첨 이력 버전이 변경되어 다시 확인해야 합니다.");
        }
        meterRegistry.counter("kraft_lotto_recommend_success_total", "strategy", ctx.strategy()).increment();
        List<List<Integer>> recommendations = items.stream().map(RecommendationItemView::numbers).toList();

        String algorithmVersion = algorithmVersionOf(ctx.strategy());

        Long setId = null;
        OffsetDateTime createdAt = null;
        if (clientTokenHash != null || ownerUserId != null) {
            createdAt = OffsetDateTime.now(clock);
            setId = ownerUserId != null
                    ? recommendationSetHistoryService.persistForOwner(ownerUserId, ctx.strategy(), algorithmVersion,
                            snapshot.historyThroughRound(), EXCLUSION_POLICY_VERSION,
                            ctx.locked(), ctx.excluded().stream().sorted().toList(), items, createdAt)
                    : recommendationSetHistoryService.persist(clientTokenHash, ctx.strategy(), algorithmVersion,
                            snapshot.historyThroughRound(), EXCLUSION_POLICY_VERSION,
                            ctx.locked(), ctx.excluded().stream().sorted().toList(), items, createdAt);
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

    private ApiException fail(ApiErrorCode errorCode, String message) {
        meterRegistry.counter("kraft_lotto_recommend_failures_total", "code", errorCode.name()).increment();
        return new ApiException(errorCode, message);
    }
}
