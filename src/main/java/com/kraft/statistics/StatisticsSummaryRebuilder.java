package com.kraft.statistics;

import com.kraft.common.config.CacheConfig;
import com.kraft.common.lotto.BallClassification;
import com.kraft.common.lotto.SumBuckets;
import com.kraft.winningnumber.WinningBallsOnly;
import com.kraft.winningnumber.WinningNumberRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이 리빌더는 항상 완전 도메인(빈도 45행, 패턴 7+7+5=19행, 동반번호 990행)을 0으로
 * 채워 기록한다. 관측되지 않은 키·쌍도 삭제하지 않고 count=0인 행으로 남긴다 —
 * {@link WinningStatisticsCacheService}의 완전성 검사가 이 고정 도메인 계약에
 * 의존하며, 관측된 키만 기록하고 나머지를 지우는 부분 도메인 방식을 쓰면 실제
 * 라운드 수가 적을수록(신규 설치·희소 이력·재해복구 직후) 완전성 검사를 영원히
 * 통과하지 못해 읽기마다 재계산이 재발동하는 루프가 생긴다.
 */
@Service
public class StatisticsSummaryRebuilder {

    private static final Logger log = LoggerFactory.getLogger(StatisticsSummaryRebuilder.class);
    private static final String REBUILD_LOCK_NAME = "statistics-summary-rebuild";
    private static final Duration REBUILD_LOCK_AT_MOST_FOR = Duration.ofMinutes(10);
    private static final Duration REBUILD_LOCK_AT_LEAST_FOR = Duration.ZERO;

    /**
     * BE-SEC-01(docs/improvement.md) 계열 정리: {@code outcome} 태그 값 4가지는 이미
     * 이 클래스 안에서만 쓰이는 고정 리터럴이었다(외부 입력 아님) — 값 자체(success/
     * empty/skipped/failure)는 바꾸지 않는다. Grafana 대시보드와
     * {@code kraft_alerts.yml:181}의 {@code outcome="failure"} 경보가 이 태그 값을
     * 그대로 참조하므로, 여기서 바꾸는 것은 태그 값이 아니라 "요청마다 Counter.builder를
     * 새로 만들지 않는다"는 등록 방식뿐이다(TD-022).
     */
    private enum RebuildOutcome {
        SUCCESS("success"),
        EMPTY("empty"),
        SKIPPED("skipped"),
        FAILURE("failure");

        private final String tagValue;

        RebuildOutcome(String tagValue) {
            this.tagValue = tagValue;
        }
    }

    private final WinningNumberRepository winningNumberRepository;
    private final FrequencySummaryRepository frequencySummaryRepository;
    private final PatternStatsSummaryRepository patternStatsSummaryRepository;
    private final CompanionPairSummaryRepository companionPairSummaryRepository;
    private final StatisticsProjectionStateRepository statisticsProjectionStateRepository;
    private final Clock clock;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;
    private final Map<RebuildOutcome, Counter> rebuildOutcomeCounters;

    public StatisticsSummaryRebuilder(WinningNumberRepository winningNumberRepository,
                                      FrequencySummaryRepository frequencySummaryRepository,
                                      PatternStatsSummaryRepository patternStatsSummaryRepository,
                                      CompanionPairSummaryRepository companionPairSummaryRepository,
                                      StatisticsProjectionStateRepository statisticsProjectionStateRepository,
                                      Clock clock,
                                      LockProvider lockProvider,
                                      MeterRegistry meterRegistry,
                                      PlatformTransactionManager transactionManager) {
        this.winningNumberRepository = winningNumberRepository;
        this.frequencySummaryRepository = frequencySummaryRepository;
        this.patternStatsSummaryRepository = patternStatsSummaryRepository;
        this.companionPairSummaryRepository = companionPairSummaryRepository;
        this.statisticsProjectionStateRepository = statisticsProjectionStateRepository;
        this.clock = clock;
        this.lockingTaskExecutor = new DefaultLockingTaskExecutor(lockProvider);
        this.meterRegistry = meterRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rebuildOutcomeCounters = new EnumMap<>(RebuildOutcome.class);
        for (RebuildOutcome outcome : RebuildOutcome.values()) {
            rebuildOutcomeCounters.put(outcome, Counter.builder("statistics.summary.rebuild")
                    .description("Statistics summary rebuild outcomes")
                    .tag("outcome", outcome.tagValue)
                    .register(meterRegistry));
        }
    }

    @CacheEvict(value = {CacheConfig.STATS_FREQUENCY, CacheConfig.STATS_FREQUENCY_BY_LIMIT,
                CacheConfig.STATS_PATTERN, CacheConfig.STATS_COMPANION}, allEntries = true)
    public void rebuildAllSummaries() {
        LockingTaskExecutor.TaskResult<Boolean> result;
        try {
            result = lockingTaskExecutor.executeWithLock(
                    () -> transactionTemplate.execute(status -> rebuildAllSummariesInternal()),
                    new LockConfiguration(
                            clock.instant(),
                            REBUILD_LOCK_NAME,
                            REBUILD_LOCK_AT_MOST_FOR,
                            REBUILD_LOCK_AT_LEAST_FOR
                    )
            );
        } catch (Throwable throwable) {
            recordRebuildOutcome(RebuildOutcome.FAILURE);
            throw new IllegalStateException("Failed to rebuild statistics summaries", throwable);
        }

        if (!result.wasExecuted()) {
            log.info("statistics summary rebuild skipped because another instance holds the lock");
            recordRebuildOutcome(RebuildOutcome.SKIPPED);
        }
    }

    private boolean rebuildAllSummariesInternal() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<WinningBallsOnly> all = winningNumberRepository.findAllBalls();
            OffsetDateTime now = OffsetDateTime.now(clock);

            log.info("Starting statistics summary rebuild: totalRounds={}", all.size());

            // 원본이 비어도(예: DB 초기화·복원 직후) 별도 분기 없이 아래 세 rebuild가 그대로
            // 실행된다 — 각 메서드가 항상 완전 도메인을 0으로 채우므로, 관측된 라운드가
            // 0개면 자연히 "완전한 0 프로젝션"이 만들어진다. 예전에는 이 경우 summary
            // 테이블을 통째로 지웠는데, 그러면 읽기 측 완전성 검사(45/7/7/5/990)가 항상
            // 실패해 매 읽기마다 재계산이 재발동했다.
            rebuildFrequency(all, now);
            rebuildPatterns(all, now);
            rebuildCompanions(all, now);
            int lastProcessedRound = all.stream().mapToInt(WinningBallsOnly::getRound).max().orElse(0);
            recordProjectionState(lastProcessedRound, all.size(), now);
            recordRebuildOutcome(all.isEmpty() ? RebuildOutcome.EMPTY : RebuildOutcome.SUCCESS);
            log.info("Completed statistics summary rebuild");
            return true;
        } catch (RuntimeException exception) {
            recordRebuildOutcome(RebuildOutcome.FAILURE);
            throw exception;
        } finally {
            sample.stop(Timer.builder("statistics.summary.rebuild.duration")
                    .description("Time spent rebuilding cached statistics summaries")
                    .register(meterRegistry));
        }
    }

    /**
     * KB-16: summary 테이블 쓰기와 같은(REQUIRES_NEW) 트랜잭션 안에서 프로젝션 상태를
     * 원자적으로 갱신한다 — 이 메서드가 커밋되지 않으면 summary도 커밋되지 않으므로,
     * 상태 행의 sourceRowCount는 항상 방금 커밋된 summary 내용과 같은 스냅숏을 가리킨다.
     */
    private void recordProjectionState(int lastProcessedRound, long sourceRowCount, OffsetDateTime now) {
        StatisticsProjectionState state = statisticsProjectionStateRepository
                .findById(StatisticsProjectionState.SINGLETON_ID)
                .orElseGet(() -> new StatisticsProjectionState(StatisticsProjectionState.SINGLETON_ID));
        state.recordSuccess(lastProcessedRound, sourceRowCount, now);
        statisticsProjectionStateRepository.save(state);
    }

    private void recordRebuildOutcome(RebuildOutcome outcome) {
        rebuildOutcomeCounters.get(outcome).increment();
    }

    private void rebuildFrequency(List<WinningBallsOnly> rounds, OffsetDateTime now) {
        Map<Integer, Integer> freqMap = new HashMap<>();
        Map<Integer, Integer> lastRoundMap = new HashMap<>();

        for (WinningBallsOnly w : rounds) {
            for (int ball : List.of(w.getN1(), w.getN2(), w.getN3(), w.getN4(), w.getN5(), w.getN6())) {
                freqMap.merge(ball, 1, Integer::sum);
                lastRoundMap.merge(ball, w.getRound(), Math::max);
            }
        }

        Map<Integer, FrequencySummary> existing = frequencySummaryRepository.findAll().stream()
                .collect(Collectors.toMap(FrequencySummary::getBallNumber, s -> s));

        List<FrequencySummary> toSave = new ArrayList<>();
        for (int ball = 1; ball <= 45; ball++) {
            int freq = freqMap.getOrDefault(ball, 0);
            int last = lastRoundMap.getOrDefault(ball, 0);
            FrequencySummary row = existing.get(ball);
            if (row != null) {
                row.update(freq, last, now);
            } else {
                toSave.add(new FrequencySummary(ball, freq, last, now));
            }
        }
        frequencySummaryRepository.saveAll(toSave);
    }

    private void rebuildPatterns(List<WinningBallsOnly> rounds, OffsetDateTime now) {
        // 완전 도메인을 0으로 미리 채운 뒤 관측된 라운드로 오버레이한다 — 한 번도 관측되지
        // 않은 키도 이 맵에서 사라지지 않고 count=0으로 남아야 읽기 측 완전성 계약을 만족한다.
        Map<String, Integer> oddCountMap = new HashMap<>(
                StatisticsSummaryDomain.zeroFilled(StatisticsSummaryDomain.ODD_COUNT_KEYS));
        Map<String, Integer> highCountMap = new HashMap<>(
                StatisticsSummaryDomain.zeroFilled(StatisticsSummaryDomain.HIGH_COUNT_KEYS));
        Map<String, Integer> sumBucketMap = new HashMap<>(
                StatisticsSummaryDomain.zeroFilled(SumBuckets.ALL_KEYS));

        for (WinningBallsOnly w : rounds) {
            List<Integer> balls = List.of(w.getN1(), w.getN2(), w.getN3(), w.getN4(), w.getN5(), w.getN6());

            String oddKey = String.valueOf(balls.stream().filter(BallClassification::isOdd).count());
            oddCountMap.merge(oddKey, 1, Integer::sum);

            String highKey = String.valueOf(balls.stream().filter(BallClassification::isHigh).count());
            highCountMap.merge(highKey, 1, Integer::sum);

            int sum = balls.stream().mapToInt(Integer::intValue).sum();
            String bucket = SumBuckets.bucketOf(sum);
            sumBucketMap.merge(bucket, 1, Integer::sum);
        }

        Map<String, PatternStatsSummary> existing = patternStatsSummaryRepository.findAll().stream()
                .collect(Collectors.toMap(s -> patternKey(s.getStatType(), s.getBucketKey()), s -> s));

        List<PatternStatsSummary> toSave = new ArrayList<>();
        upsertPatternRows(WinningStatisticsCacheService.TYPE_ODD_COUNT, oddCountMap, now, existing, toSave);
        upsertPatternRows(WinningStatisticsCacheService.TYPE_HIGH_COUNT, highCountMap, now, existing, toSave);
        upsertPatternRows(WinningStatisticsCacheService.TYPE_SUM_BUCKET, sumBucketMap, now, existing, toSave);
        patternStatsSummaryRepository.saveAll(toSave);
    }

    private void upsertPatternRows(String statType, Map<String, Integer> data, OffsetDateTime now,
                                   Map<String, PatternStatsSummary> existing, List<PatternStatsSummary> toSave) {
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            String key = patternKey(statType, entry.getKey());
            PatternStatsSummary row = existing.get(key);
            if (row != null) {
                row.update(entry.getValue(), now);
            } else {
                toSave.add(new PatternStatsSummary(statType, entry.getKey(), entry.getValue(), now));
            }
        }
    }

    private static String patternKey(String statType, String bucketKey) {
        return statType + "::" + bucketKey;
    }

    private void rebuildCompanions(List<WinningBallsOnly> rounds, OffsetDateTime now) {
        // 45C2=990쌍 전체를 0으로 미리 채운다 — 실제 라운드 수가 적을수록 관측되지 않는
        // 쌍이 많아지므로(예: 1라운드면 990쌍 중 15쌍만 관측됨), 관측된 쌍만 기록하고
        // 나머지를 지우면 읽기 측 완전성 검사(count==990)를 영원히 통과할 수 없다.
        List<int[]> allPairs = StatisticsSummaryDomain.allCompanionPairs();
        Map<String, int[]> pairMap = new HashMap<>();
        for (int[] pair : allPairs) {
            pairMap.put(pair[0] + "_" + pair[1], new int[] {pair[0], pair[1], 0});
        }

        for (WinningBallsOnly w : rounds) {
            List<Integer> balls = new ArrayList<>(
                    List.of(w.getN1(), w.getN2(), w.getN3(), w.getN4(), w.getN5(), w.getN6()));
            Collections.sort(balls);
            for (int i = 0; i < balls.size() - 1; i++) {
                for (int j = i + 1; j < balls.size(); j++) {
                    pairMap.get(balls.get(i) + "_" + balls.get(j))[2]++;
                }
            }
        }

        Map<String, CompanionPairSummary> existing = companionPairSummaryRepository.findAll().stream()
                .collect(Collectors.toMap(s -> s.getBallA() + "_" + s.getBallB(), s -> s));

        List<CompanionPairSummary> toSave = new ArrayList<>();
        for (int[] pair : allPairs) {
            String key = pair[0] + "_" + pair[1];
            int coCount = pairMap.get(key)[2];
            CompanionPairSummary row = existing.get(key);
            if (row != null) {
                row.update(coCount, now);
            } else {
                toSave.add(new CompanionPairSummary(pair[0], pair[1], coCount, now));
            }
        }
        companionPairSummaryRepository.saveAll(toSave);
    }

}
