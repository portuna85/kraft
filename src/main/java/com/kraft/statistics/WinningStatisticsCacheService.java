package com.kraft.statistics;

import com.kraft.common.config.CacheConfig;
import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.BallClassification;
import com.kraft.common.lotto.SumBuckets;
import com.kraft.winningnumber.FirstPrizeHistoryDto;
import com.kraft.winningnumber.FirstPrizeHistoryService;
import com.kraft.winningnumber.WinningBallsOnly;
import com.kraft.winningnumber.WinningNumberRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WinningStatisticsCacheService {

    private static final Logger log = LoggerFactory.getLogger(WinningStatisticsCacheService.class);
    // 45개 번호 전체 쌍 조합 수(45C2=990). 클라이언트 번호별 필터가 전체 쌍을 대상으로
    // 동작하려면 전체를 전달해야 한다 — 상위 N개만 보내면 N 밖의 번호는 "기록 없음"으로 오표시된다.
    // 완전 도메인 정의는 StatisticsSummaryDomain이 단일 소스다(생산자·소비자 도메인 불일치 방지).
    private static final int COMPANION_TOP_LIMIT = StatisticsSummaryDomain.COMPANION_PAIR_COUNT;

    // pattern stat_type 상수 (StatisticsSummaryRebuilder에서도 참조)
    static final String TYPE_ODD_COUNT = "ODD_COUNT";
    static final String TYPE_HIGH_COUNT = "HIGH_COUNT";
    static final String TYPE_SUM_BUCKET = "SUM_BUCKET";

    // BE-STAT-01(docs/improvement.md): 완전성 검사 실패 후 재계산 폴백이 실제로 어떻게
    // 끝났는지 — SKIPPED(lock 경합)면 짧은 bounded retry 후 완전성만 재검사하고, 그래도
    // 실패하면 조용히 기존/불완전 데이터를 돌려주지 않고 던진다("완전성이 검증된
    // last-known-good snapshot이 있을 때만 명시적 stale 응답을 허용한다").
    private enum ReadFallbackOutcome {
        REBUILT("rebuilt"),
        SKIPPED_COMPLETE("skipped_complete"),
        FAILED("failed"),
        STILL_INCOMPLETE("still_incomplete");

        private final String tagValue;

        ReadFallbackOutcome(String tagValue) {
            this.tagValue = tagValue;
        }
    }

    private static final int SKIPPED_RETRY_MAX_ATTEMPTS = 3;
    private static final long SKIPPED_RETRY_DELAY_MILLIS = 200;

    private final WinningNumberRepository winningNumberRepository;
    private final FrequencySummaryRepository frequencySummaryRepository;
    private final PatternStatsSummaryRepository patternStatsSummaryRepository;
    private final CompanionPairSummaryRepository companionPairSummaryRepository;
    private final StatisticsProjectionStateRepository statisticsProjectionStateRepository;
    private final StatisticsSummaryRebuilder summaryRebuilder;
    private final FirstPrizeHistoryService firstPrizeHistoryService;
    private final Map<ReadFallbackOutcome, Counter> readFallbackOutcomeCounters;

    public WinningStatisticsCacheService(WinningNumberRepository winningNumberRepository,
                                         FrequencySummaryRepository frequencySummaryRepository,
                                         PatternStatsSummaryRepository patternStatsSummaryRepository,
                                         CompanionPairSummaryRepository companionPairSummaryRepository,
                                         StatisticsProjectionStateRepository statisticsProjectionStateRepository,
                                         StatisticsSummaryRebuilder summaryRebuilder,
                                         FirstPrizeHistoryService firstPrizeHistoryService,
                                         MeterRegistry meterRegistry) {
        this.winningNumberRepository = winningNumberRepository;
        this.frequencySummaryRepository = frequencySummaryRepository;
        this.patternStatsSummaryRepository = patternStatsSummaryRepository;
        this.companionPairSummaryRepository = companionPairSummaryRepository;
        this.statisticsProjectionStateRepository = statisticsProjectionStateRepository;
        this.summaryRebuilder = summaryRebuilder;
        this.firstPrizeHistoryService = firstPrizeHistoryService;
        // TD-022 계열: StatisticsSummaryRebuilder의 rebuildOutcomeCounters와 같은 hoisting
        // 패턴 — 요청마다 Counter.builder(...)를 새로 만들지 않는다.
        this.readFallbackOutcomeCounters = new EnumMap<>(ReadFallbackOutcome.class);
        for (ReadFallbackOutcome outcome : ReadFallbackOutcome.values()) {
            readFallbackOutcomeCounters.put(outcome, Counter.builder("statistics.read.fallback")
                    .description("Statistics read-path fallback outcomes after a completeness check failure")
                    .tag("outcome", outcome.tagValue)
                    .register(meterRegistry));
        }
    }

    // ──────────────────────────────────────────────
    // Public API — summary → 폴백 재계산 구조
    // ──────────────────────────────────────────────

    @Cacheable(value = CacheConfig.STATS_FREQUENCY, sync = true)
    public FrequencyStatsResponse getFrequencyStats() {
        AtomicReference<List<FrequencySummary>> summariesRef =
                new AtomicReference<>(frequencySummaryRepository.findAllByOrderByBallNumberAsc());

        // rebuildFrequency()는 1~45번 전부에 대해 행을 만들므로, 정상 상태라면 항상 45개다.
        // 45개가 아니면 완전히 비었을 때뿐 아니라 일부만 남은 부분 손상도 재계산 대상이다(T2).
        if (summariesRef.get().size() != 45) {
            log.info("빈도 summary 없음 또는 불완전(size={}) — 재계산 시작", summariesRef.get().size());
            ensureCompleteOrThrow(() -> summariesRef.get().size() == 45,
                    () -> summariesRef.set(frequencySummaryRepository.findAllByOrderByBallNumberAsc()));
        }

        List<BallFrequencyDto> frequencies = summariesRef.get().stream()
                .map(s -> new BallFrequencyDto(s.getBallNumber(), s.getFrequency(), s.getLastRound()))
                .toList();
        return toFrequencyResponse(sampleRoundCount(), frequencies);
    }

    @Cacheable(value = CacheConfig.STATS_FREQUENCY_BY_LIMIT, key = "#limit", sync = true)
    public FrequencyStatsResponse getFrequencyStatsByLimit(int limit) {
        List<WinningBallsOnly> rounds = winningNumberRepository
                .findBallsByOrderByRoundDesc(PageRequest.of(0, limit));
        return computeFrequencyResponse(rounds);
    }

    private FrequencyStatsResponse computeFrequencyResponse(List<WinningBallsOnly> rounds) {
        Map<Integer, Integer> freqMap = new HashMap<>();
        Map<Integer, Integer> lastRoundMap = new HashMap<>();

        for (WinningBallsOnly w : rounds) {
            for (int ball : List.of(w.getN1(), w.getN2(), w.getN3(), w.getN4(), w.getN5(), w.getN6())) {
                freqMap.merge(ball, 1, Integer::sum);
                lastRoundMap.merge(ball, w.getRound(), Math::max);
            }
        }

        List<BallFrequencyDto> frequencies = new ArrayList<>();
        for (int ball = 1; ball <= 45; ball++) {
            frequencies.add(new BallFrequencyDto(
                    ball,
                    freqMap.getOrDefault(ball, 0),
                    lastRoundMap.getOrDefault(ball, 0)
            ));
        }
        // limit 요청은 실제로 반환된 표본 크기(rounds.size())를 totalRounds로 쓴다. limit이
        // 실제 저장된 회차 수보다 크면(예: 500회 요청인데 200회밖에 없음) rounds.size()가
        // limit보다 작아지므로, latestRound나 요청 limit을 그대로 쓰면 백분율 분모가 틀린다.
        return toFrequencyResponse(rounds.size(), frequencies);
    }

    private static final RankedCombinationDto EMPTY_RANKED_GROUP =
            new RankedCombinationDto(List.of(), false, List.of());

    private FrequencyStatsResponse toFrequencyResponse(int totalRounds, List<BallFrequencyDto> frequencies) {
        // 회차 데이터가 아직 없으면(초기 상태) summary가 45개 미만일 수 있다 — top/bottom 6을
        // 구성할 수 없으므로 빈 그룹으로 채운다.
        if (frequencies.size() < 6) {
            return new FrequencyStatsResponse(totalRounds, frequencies, EMPTY_RANKED_GROUP, EMPTY_RANKED_GROUP);
        }
        List<BallFrequencyDto> byFreqDesc = frequencies.stream()
                .sorted(Comparator.comparingInt(BallFrequencyDto::frequency).reversed())
                .toList();
        List<BallFrequencyDto> byFreqAsc = frequencies.stream()
                .sorted(Comparator.comparingInt(BallFrequencyDto::frequency))
                .toList();
        RankedCombinationDto topSix = rankedGroup(byFreqDesc.subList(0, 6));
        RankedCombinationDto bottomSix = rankedGroup(byFreqAsc.subList(0, 6));
        return new FrequencyStatsResponse(totalRounds, frequencies, topSix, bottomSix);
    }

    private RankedCombinationDto rankedGroup(List<BallFrequencyDto> six) {
        List<BallFrequencyDto> sortedByBall = six.stream()
                .sorted(Comparator.comparingInt(BallFrequencyDto::ballNumber))
                .toList();
        List<Integer> numbers = sortedByBall.stream().map(BallFrequencyDto::ballNumber).toList();
        List<FirstPrizeHistoryDto> history = firstPrizeHistoryService.findByNumbers(numbers);
        return new RankedCombinationDto(sortedByBall, history);
    }

    @Cacheable(value = CacheConfig.STATS_PATTERN, sync = true)
    public PatternStatsResponse getPatternStats() {
        AtomicReference<List<PatternStatsSummary>> oddRowsRef =
                new AtomicReference<>(patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_ODD_COUNT));
        AtomicReference<List<PatternStatsSummary>> highRowsRef =
                new AtomicReference<>(patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_HIGH_COUNT));
        AtomicReference<List<PatternStatsSummary>> sumRowsRef =
                new AtomicReference<>(patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_SUM_BUCKET));

        // 예전에는 oddRows가 비었을 때만 재계산했다 — HIGH_COUNT·SUM_BUCKET 버킷이 일부만
        // 누락된 부분 손상은 놓쳤다(T3). 세 버킷 타입 모두 개수와 키 집합이 기대값과
        // 정확히 일치하는지 확인한다.
        if (!hasAllKeys(oddRowsRef.get(), StatisticsSummaryDomain.ODD_COUNT_KEYS)
                || !hasAllKeys(highRowsRef.get(), StatisticsSummaryDomain.HIGH_COUNT_KEYS)
                || !hasAllKeys(sumRowsRef.get(), SumBuckets.ALL_KEYS)) {
            log.info("패턴 summary 불완전(odd={}, high={}, sum={}) — 재계산 시작",
                    oddRowsRef.get().size(), highRowsRef.get().size(), sumRowsRef.get().size());
            ensureCompleteOrThrow(
                    () -> hasAllKeys(oddRowsRef.get(), StatisticsSummaryDomain.ODD_COUNT_KEYS)
                            && hasAllKeys(highRowsRef.get(), StatisticsSummaryDomain.HIGH_COUNT_KEYS)
                            && hasAllKeys(sumRowsRef.get(), SumBuckets.ALL_KEYS),
                    () -> {
                        oddRowsRef.set(patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_ODD_COUNT));
                        highRowsRef.set(patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_HIGH_COUNT));
                        sumRowsRef.set(patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_SUM_BUCKET));
                    });
        }

        return new PatternStatsResponse(sampleRoundCount(), toPatternDto(oddRowsRef.get()),
                toPatternDto(highRowsRef.get()), toPatternDto(sumRowsRef.get()));
    }

    @Cacheable(value = CacheConfig.STATS_COMPANION, sync = true)
    public CompanionStatsResponse getCompanionStats() {
        AtomicReference<List<CompanionPairSummary>> pairsRef = new AtomicReference<>(companionPairSummaryRepository
                .findAllByOrderByCoCountDescBallAAscBallBAsc(PageRequest.of(0, COMPANION_TOP_LIMIT)));

        // 예전에는 pairs가 비었을 때만 재계산했다 — 990쌍(45C2) 중 일부만 누락된 부분 손상은
        // 놓쳤다(T4). BE-STAT-03: 완전성 판정에 별도 count() 쿼리를 쓰지 않는다 — 위 조회가
        // 이미 COMPANION_TOP_LIMIT(990, 이론적 최댓값)로 페이지 제한돼 있어 테이블 전체 행 수가
        // 990 미만이면 이 조회 자체가 990개 미만을 돌려준다. size()가 count()와 동치이므로
        // 쿼리를 하나 아예 없앤다(예전엔 조건 판정 + 로그 메시지용으로 count()를 두 번 불렀다).
        int pairCount = pairsRef.get().size();
        if (pairCount != COMPANION_TOP_LIMIT) {
            log.info("동반 summary 불완전(count={}) — 재계산 시작", pairCount);
            ensureCompleteOrThrow(
                    () -> pairsRef.get().size() == COMPANION_TOP_LIMIT,
                    () -> pairsRef.set(companionPairSummaryRepository
                            .findAllByOrderByCoCountDescBallAAscBallBAsc(PageRequest.of(0, COMPANION_TOP_LIMIT))));
        }

        List<CompanionPairDto> topPairs = pairsRef.get().stream()
                .map(p -> new CompanionPairDto(p.getBallA(), p.getBallB(), p.getCoCount()))
                .toList();
        return new CompanionStatsResponse(sampleRoundCount(), topPairs);
    }

    @Cacheable(value = CacheConfig.STATS_COMPANION, key = "#ball", sync = true)
    public CompanionStatsResponse getCompanionStatsByBall(int ball) {
        // per-ball 결과 크기(최대 44)가 아니라 전체 테이블 행 수(990)로 완전성을 판단한다 —
        // 특정 번호의 결과만 보면 항상 44개 이하가 정상이라 완전성 판단 기준이 될 수 없다(T4).
        // count()는 조건 판정에 한 번만 부르고 재사용한다(BE-STAT-03).
        long count = companionPairSummaryRepository.count();
        if (count != COMPANION_TOP_LIMIT) {
            log.info("동반 summary 불완전(count={}) — 재계산 시작", count);
            ensureCompleteOrThrow(() -> companionPairSummaryRepository.count() == COMPANION_TOP_LIMIT, () -> { });
        }

        List<CompanionPairSummary> pairs = companionPairSummaryRepository
                .findByBallAOrBallBOrderByCoCountDescBallAAscBallBAsc(ball, ball);

        List<CompanionPairDto> topPairs = pairs.stream()
                .map(p -> new CompanionPairDto(p.getBallA(), p.getBallB(), p.getCoCount()))
                .toList();
        return new CompanionStatsResponse(sampleRoundCount(), topPairs);
    }

    private static boolean hasAllKeys(List<PatternStatsSummary> rows, Set<String> expectedKeys) {
        if (rows.size() != expectedKeys.size()) {
            return false;
        }
        return rows.stream().map(PatternStatsSummary::getBucketKey).collect(Collectors.toSet()).equals(expectedKeys);
    }

    public AnalysisResponse analyze(List<Integer> rawNumbers) {
        List<Integer> numbers = rawNumbers.stream().sorted().toList();

        int oddCount = (int) numbers.stream().filter(BallClassification::isOdd).count();
        int evenCount = numbers.size() - oddCount;
        int highCount = (int) numbers.stream().filter(BallClassification::isHigh).count();
        int lowCount = numbers.size() - highCount;
        int sum = numbers.stream().mapToInt(Integer::intValue).sum();
        String sumBucket = SumBuckets.bucketOf(sum);

        int consecutivePairCount = 0;
        for (int i = 0; i < numbers.size() - 1; i++) {
            if (numbers.get(i + 1) - numbers.get(i) == 1) {
                consecutivePairCount++;
            }
        }

        List<AnalysisResponse.RangeDistribution> ranges = computeRangeDistribution(numbers);
        List<FirstPrizeHistoryDto> history = firstPrizeHistoryService.findByNumbers(numbers);

        return new AnalysisResponse(numbers, oddCount, evenCount, lowCount, highCount,
                sum, sumBucket, consecutivePairCount, ranges, !history.isEmpty(), history);
    }

    /**
     * BE-STAT-01(docs/improvement.md): 이 메서드는 완전성 검사가 이미 실패한 뒤에만 불린다 —
     * 즉 호출 시점에 "완전성이 검증된 last-known-good snapshot"은 없다. 그래서 예전처럼
     * 재계산 실패(lock 경합이 아닌 진짜 실패)를 조용히 삼키고 기존/불완전 데이터로 200을
     * 만들지 않는다: 재계산이 실패하면 그 예외를 그대로 전파하고, lock 경합(SKIPPED)이면
     * 짧은 bounded retry로 완전성만 재검사하며, 그래도 불완전하면 명시적으로 던진다
     * ({@link ApiErrorCode#STATISTICS_NOT_READY}, 503). 예전엔 이 네 갈래가 전부 200으로
     * 수렴했다.
     *
     * @param isComplete 현재 상태가 완전한지 판단(재시도마다 다시 평가됨)
     * @param reload     재계산/재시도 이후 판단·응답에 쓸 데이터를 다시 읽어오는 부수효과
     */
    private void ensureCompleteOrThrow(BooleanSupplier isComplete, Runnable reload) {
        StatisticsSummaryRebuilder.RebuildOutcome outcome;
        try {
            outcome = summaryRebuilder.rebuildAllSummaries();
        } catch (RuntimeException ex) {
            recordReadFallback(ReadFallbackOutcome.FAILED);
            throw ex;
        }

        reload.run();

        if (outcome != StatisticsSummaryRebuilder.RebuildOutcome.SKIPPED) {
            if (isComplete.getAsBoolean()) {
                recordReadFallback(ReadFallbackOutcome.REBUILT);
                return;
            }
            // H-01 이후 rebuilder는 실행되면 항상 완전 도메인을 만든다 — 여기 도달하면
            // rebuilder 자체의 계약이 깨진 것이라 진짜 이상 상태다.
            recordReadFallback(ReadFallbackOutcome.STILL_INCOMPLETE);
            throw new ApiException(ApiErrorCode.STATISTICS_NOT_READY, "통계 재계산 후에도 데이터가 불완전합니다.");
        }

        // lock을 다른 인스턴스/스레드가 들고 있었다(SKIPPED) — 그 쪽이 끝날 때까지 짧게
        // 폴링한다. 재계산을 다시 시도하지 않는다: 여전히 잠겨 있을 것이므로 의미가 없다.
        for (int attempt = 0; attempt < SKIPPED_RETRY_MAX_ATTEMPTS; attempt++) {
            if (isComplete.getAsBoolean()) {
                recordReadFallback(ReadFallbackOutcome.SKIPPED_COMPLETE);
                return;
            }
            sleepQuietly(SKIPPED_RETRY_DELAY_MILLIS);
            reload.run();
        }
        recordReadFallback(ReadFallbackOutcome.STILL_INCOMPLETE);
        throw new ApiException(ApiErrorCode.STATISTICS_NOT_READY, "다른 인스턴스의 재계산이 아직 끝나지 않았습니다.");
    }

    private void recordReadFallback(ReadFallbackOutcome outcome) {
        readFallbackOutcomeCounters.get(outcome).increment();
    }

    // winningnumber.SleepUtils는 그 패키지 전용으로 의도적으로 좁혀둔 헬퍼라(재수집 재시도
    // 전용) 재사용하지 않고 같은 3줄 패턴을 여기 복제한다.
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ──────────────────────────────────────────────
    // 유틸리티
    // ──────────────────────────────────────────────

    /**
     * summary 기반(전체 이력) 응답의 totalRounds. KB-16: 예전에는 winning_numbers의
     * 실시간 count()를 썼는데, summary(분자)는 마지막 rebuild 시점 스냅숏인 반면 이 값(분모)은
     * 서빙 시점 값이라 그 사이 새 회차가 수집되면 분자·분모가 서로 다른 시점을 가리켰다.
     * 프로젝션 상태에 rebuild가 원자적으로 남긴 sourceRowCount를 대신 써서, summary 내용과
     * 항상 같은 스냅숏을 보장한다. 상태 행이 아직 없으면(이론상 마이그레이션이 항상 백필하므로
     * 발생하지 않음) 실시간 count로 폴백한다.
     */
    private int sampleRoundCount() {
        return statisticsProjectionStateRepository.findById(StatisticsProjectionState.SINGLETON_ID)
                .map(state -> (int) state.getSourceRowCount())
                .orElseGet(() -> (int) winningNumberRepository.count());
    }

    private static List<AnalysisResponse.RangeDistribution> computeRangeDistribution(List<Integer> numbers) {
        int[] ranges = new int[5];
        for (int n : numbers) {
            if (n <= 9) {
                ranges[0]++;
            } else if (n <= 19) {
                ranges[1]++;
            } else if (n <= 29) {
                ranges[2]++;
            } else if (n <= 39) {
                ranges[3]++;
            } else {
                ranges[4]++;
            }
        }
        return List.of(
                new AnalysisResponse.RangeDistribution("1-9", ranges[0]),
                new AnalysisResponse.RangeDistribution("10-19", ranges[1]),
                new AnalysisResponse.RangeDistribution("20-29", ranges[2]),
                new AnalysisResponse.RangeDistribution("30-39", ranges[3]),
                new AnalysisResponse.RangeDistribution("40-45", ranges[4])
        );
    }

    private static List<PatternBucketDto> toPatternDto(List<PatternStatsSummary> rows) {
        return rows.stream()
                .map(r -> new PatternBucketDto(r.getBucketKey(), r.getCountVal()))
                .toList();
    }
}
