package com.kraft.statistics;

import com.kraft.common.config.CacheConfig;
import com.kraft.common.lotto.BallClassification;
import com.kraft.common.lotto.SumBuckets;
import com.kraft.winningnumber.FirstPrizeHistoryDto;
import com.kraft.winningnumber.FirstPrizeHistoryService;
import com.kraft.winningnumber.WinningBallsOnly;
import com.kraft.winningnumber.WinningNumberRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class WinningStatisticsCacheService {

    private static final Logger log = LoggerFactory.getLogger(WinningStatisticsCacheService.class);
    // 45개 번호 전체 쌍 조합 수(45C2=990). 클라이언트 번호별 필터가 전체 쌍을 대상으로
    // 동작하려면 전체를 전달해야 한다 — 상위 N개만 보내면 N 밖의 번호는 "기록 없음"으로 오표시된다.
    private static final int COMPANION_TOP_LIMIT = 990;

    // pattern stat_type 상수 (StatisticsSummaryRebuilder에서도 참조)
    static final String TYPE_ODD_COUNT = "ODD_COUNT";
    static final String TYPE_HIGH_COUNT = "HIGH_COUNT";
    static final String TYPE_SUM_BUCKET = "SUM_BUCKET";

    // 홀수 개수·고번호 개수 버킷은 0~6개(6개 번호 중 몇 개가 조건을 만족하는지) 총 7가지다.
    private static final Set<String> ODD_COUNT_KEYS = Set.of("0", "1", "2", "3", "4", "5", "6");
    private static final Set<String> HIGH_COUNT_KEYS = Set.of("0", "1", "2", "3", "4", "5", "6");

    private final WinningNumberRepository winningNumberRepository;
    private final FrequencySummaryRepository frequencySummaryRepository;
    private final PatternStatsSummaryRepository patternStatsSummaryRepository;
    private final CompanionPairSummaryRepository companionPairSummaryRepository;
    private final StatisticsSummaryRebuilder summaryRebuilder;
    private final FirstPrizeHistoryService firstPrizeHistoryService;

    public WinningStatisticsCacheService(WinningNumberRepository winningNumberRepository,
                                         FrequencySummaryRepository frequencySummaryRepository,
                                         PatternStatsSummaryRepository patternStatsSummaryRepository,
                                         CompanionPairSummaryRepository companionPairSummaryRepository,
                                         StatisticsSummaryRebuilder summaryRebuilder,
                                         FirstPrizeHistoryService firstPrizeHistoryService) {
        this.winningNumberRepository = winningNumberRepository;
        this.frequencySummaryRepository = frequencySummaryRepository;
        this.patternStatsSummaryRepository = patternStatsSummaryRepository;
        this.companionPairSummaryRepository = companionPairSummaryRepository;
        this.summaryRebuilder = summaryRebuilder;
        this.firstPrizeHistoryService = firstPrizeHistoryService;
    }

    // ──────────────────────────────────────────────
    // Public API — summary → 폴백 재계산 구조
    // ──────────────────────────────────────────────

    @Cacheable(CacheConfig.STATS_FREQUENCY)
    public FrequencyStatsResponse getFrequencyStats() {
        List<FrequencySummary> summaries = frequencySummaryRepository.findAllByOrderByBallNumberAsc();

        // rebuildFrequency()는 1~45번 전부에 대해 행을 만들므로, 정상 상태라면 항상 45개다.
        // 45개가 아니면 완전히 비었을 때뿐 아니라 일부만 남은 부분 손상도 재계산 대상이다(T2).
        if (summaries.size() != 45) {
            log.info("빈도 summary 없음 또는 불완전(size={}) — 재계산 시작", summaries.size());
            rebuildSummariesIgnoringConcurrencyFailure();
            summaries = frequencySummaryRepository.findAllByOrderByBallNumberAsc();
        }

        List<BallFrequencyDto> frequencies = summaries.stream()
                .map(s -> new BallFrequencyDto(s.getBallNumber(), s.getFrequency(), s.getLastRound()))
                .toList();
        return toFrequencyResponse(sampleRoundCount(), frequencies);
    }

    @Cacheable(value = CacheConfig.STATS_FREQUENCY_BY_LIMIT, key = "#limit")
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

    @Cacheable(CacheConfig.STATS_PATTERN)
    public PatternStatsResponse getPatternStats() {
        List<PatternStatsSummary> oddRows = patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_ODD_COUNT);
        List<PatternStatsSummary> highRows = patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_HIGH_COUNT);
        List<PatternStatsSummary> sumRows = patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_SUM_BUCKET);

        // 예전에는 oddRows가 비었을 때만 재계산했다 — HIGH_COUNT·SUM_BUCKET 버킷이 일부만
        // 누락된 부분 손상은 놓쳤다(T3). 세 버킷 타입 모두 개수와 키 집합이 기대값과
        // 정확히 일치하는지 확인한다.
        if (!hasAllKeys(oddRows, ODD_COUNT_KEYS)
                || !hasAllKeys(highRows, HIGH_COUNT_KEYS)
                || !hasAllKeys(sumRows, SumBuckets.ALL_KEYS)) {
            log.info("패턴 summary 불완전(odd={}, high={}, sum={}) — 재계산 시작",
                    oddRows.size(), highRows.size(), sumRows.size());
            rebuildSummariesIgnoringConcurrencyFailure();
            oddRows = patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_ODD_COUNT);
            highRows = patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_HIGH_COUNT);
            sumRows = patternStatsSummaryRepository.findByStatTypeOrderByBucketKeyAsc(TYPE_SUM_BUCKET);
        }

        return new PatternStatsResponse(sampleRoundCount(), toPatternDto(oddRows), toPatternDto(highRows), toPatternDto(sumRows));
    }

    @Cacheable(CacheConfig.STATS_COMPANION)
    public CompanionStatsResponse getCompanionStats() {
        List<CompanionPairSummary> pairs = companionPairSummaryRepository
                .findAllByOrderByCoCountDescBallAAscBallBAsc(PageRequest.of(0, COMPANION_TOP_LIMIT));

        // 예전에는 pairs가 비었을 때만 재계산했다 — 990쌍(45C2) 중 일부만 누락된 부분 손상은
        // 놓쳤다(T4). 전체 테이블 행 수를 기준으로 완전성을 확인한다.
        if (companionPairSummaryRepository.count() != COMPANION_TOP_LIMIT) {
            log.info("동반 summary 불완전(count={}) — 재계산 시작", companionPairSummaryRepository.count());
            rebuildSummariesIgnoringConcurrencyFailure();
            pairs = companionPairSummaryRepository
                    .findAllByOrderByCoCountDescBallAAscBallBAsc(PageRequest.of(0, COMPANION_TOP_LIMIT));
        }

        List<CompanionPairDto> topPairs = pairs.stream()
                .map(p -> new CompanionPairDto(p.getBallA(), p.getBallB(), p.getCoCount()))
                .toList();
        return new CompanionStatsResponse(sampleRoundCount(), topPairs);
    }

    @Cacheable(value = CacheConfig.STATS_COMPANION, key = "#ball")
    public CompanionStatsResponse getCompanionStatsByBall(int ball) {
        // per-ball 결과 크기(최대 44)가 아니라 전체 테이블 행 수(990)로 완전성을 판단한다 —
        // 특정 번호의 결과만 보면 항상 44개 이하가 정상이라 완전성 판단 기준이 될 수 없다(T4).
        if (companionPairSummaryRepository.count() != COMPANION_TOP_LIMIT) {
            log.info("동반 summary 불완전(count={}) — 재계산 시작", companionPairSummaryRepository.count());
            rebuildSummariesIgnoringConcurrencyFailure();
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
     * 캐시 미스 폴백에서 재계산이 실패해도(동시 실행 경합으로 다른 스레드가 먼저 끝낸 경우 등)
     * 호출자에게 500을 전파하지 않는다 — 직후 재조회에서 다른 스레드가 저장한 데이터를 읽을 수 있다.
     */
    private void rebuildSummariesIgnoringConcurrencyFailure() {
        try {
            summaryRebuilder.rebuildAllSummaries();
        } catch (RuntimeException ex) {
            log.warn("summary 재계산 중 예외 발생(동시 실행 경합 가능) — 기존 데이터로 폴백: {}", ex.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // 유틸리티
    // ──────────────────────────────────────────────

    /**
     * summary 기반(전체 이력) 응답의 totalRounds. summary는 winning_numbers 전체 행으로
     * 만들어지므로, 실제 표본 수는 최신 회차 번호(latestRound)가 아니라 실제 저장된
     * 회차 수(row count)다 — 중간 누락 회차가 있으면 두 값이 달라진다(T1).
     */
    private int sampleRoundCount() {
        return (int) winningNumberRepository.count();
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
