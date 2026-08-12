package com.kraft.statistics;

import com.kraft.winningnumber.WinningNumber;
import com.kraft.winningnumber.WinningNumberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("당첨 통계 캐시 서비스 테스트")
class WinningStatisticsCacheServiceTest {

    @Autowired
    private WinningStatisticsCacheService service;

    @Autowired
    private StatisticsSummaryRebuilder summaryRebuilder;

    @Autowired
    private WinningNumberRepository winningNumberRepository;

    @Autowired
    private FrequencySummaryRepository frequencySummaryRepository;

    @Autowired
    private PatternStatsSummaryRepository patternStatsSummaryRepository;

    @Autowired
    private CompanionPairSummaryRepository companionPairSummaryRepository;

    @Autowired
    private CacheManager cacheManager;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @BeforeEach
    void setUp() {
        // getFrequencyStats() 등은 @Cacheable이고 이 테스트 클래스의 모든 메서드가 같은
        // Spring 컨텍스트(따라서 같은 캐시)를 공유하므로, 이전 테스트가 채워둔 캐시를 지우지
        // 않으면 이번 테스트의 fixture 변경이 반영되지 않은 결과를 볼 수 있다.
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        frequencySummaryRepository.deleteAll();
        patternStatsSummaryRepository.deleteAll();
        companionPairSummaryRepository.deleteAll();
        winningNumberRepository.deleteAll();

        // 회차 1: 1, 2, 3, 4, 5, 6 (홀3·짝3, 저6·고0, 합21)
        winningNumberRepository.save(round(1, 1, 2, 3, 4, 5, 6, 7));
        // 회차 2: 1, 2, 10, 20, 30, 45 (홀2·짝4, 저3·고3, 합108)
        winningNumberRepository.save(round(2, 1, 2, 10, 20, 30, 45, 8));
    }

    @Test
    @DisplayName("전체 요약 재생성 시 모든 번호의 출현 빈도가 올바르게 집계되는지 확인")
    void rebuildAllSummaries_populatesFrequencyForAllBalls() {
        summaryRebuilder.rebuildAllSummaries();

        List<FrequencySummary> all = frequencySummaryRepository.findAllByOrderByBallNumberAsc();
        assertThat(all).hasSize(45);

        // 볼 1은 두 회차 모두 등장
        FrequencySummary ball1 = all.stream().filter(s -> s.getBallNumber() == 1).findFirst().orElseThrow();
        assertThat(ball1.getFrequency()).isEqualTo(2);
        assertThat(ball1.getLastRound()).isEqualTo(2);

        // 볼 45는 회차 2에만 등장
        FrequencySummary ball45 = all.stream().filter(s -> s.getBallNumber() == 45).findFirst().orElseThrow();
        assertThat(ball45.getFrequency()).isEqualTo(1);
        assertThat(ball45.getLastRound()).isEqualTo(2);

        // 볼 7은 등장하지 않음 (보너스 번호는 집계 제외)
        FrequencySummary ball7 = all.stream().filter(s -> s.getBallNumber() == 7).findFirst().orElseThrow();
        assertThat(ball7.getFrequency()).isEqualTo(0);
    }

    @Test
    @DisplayName("전체 요약 재생성 시 패턴 통계가 올바르게 집계되는지 확인")
    void rebuildAllSummaries_populatesPatternStats() {
        summaryRebuilder.rebuildAllSummaries();

        // 회차 1: 홀수 3개(1,3,5)
        List<PatternStatsSummary> oddRows = patternStatsSummaryRepository
                .findByStatTypeOrderByBucketKeyAsc(WinningStatisticsCacheService.TYPE_ODD_COUNT);
        assertThat(oddRows).isNotEmpty();

        long countOdd3 = oddRows.stream()
                .filter(r -> "3".equals(r.getBucketKey()))
                .mapToInt(PatternStatsSummary::getCountVal).sum();
        assertThat(countOdd3).isEqualTo(1); // 회차 1만 홀수 3개
    }

    @Test
    @DisplayName("전체 요약 재생성 시 동반 출연 쌍이 올바르게 집계되는지 확인")
    void rebuildAllSummaries_populatesCompanionPairs() {
        summaryRebuilder.rebuildAllSummaries();

        // 회차 1: 1-2가 동반 출현
        CompanionPairSummary pair12 = companionPairSummaryRepository
                .findByBallAAndBallB(1, 2).orElseThrow();
        assertThat(pair12.getCoCount()).isEqualTo(2); // 두 회차 모두 1, 2 등장

        // 회차 2에만 있는 1-10 쌍
        CompanionPairSummary pair110 = companionPairSummaryRepository
                .findByBallAAndBallB(1, 10).orElseThrow();
        assertThat(pair110.getCoCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재생성을 반복해도 패턴과 동반 출현 행이 삭제 후 재삽입되지 않고 같은 식별자로 갱신된다")
    void rebuildAllSummaries_repeatedRuns_upsertSameRowsInsteadOfChurning() {
        summaryRebuilder.rebuildAllSummaries();
        Long oddRowId = patternStatsSummaryRepository
                .findByStatTypeAndBucketKey(WinningStatisticsCacheService.TYPE_ODD_COUNT, "3")
                .orElseThrow().getId();
        Long pairId = companionPairSummaryRepository.findByBallAAndBallB(1, 2).orElseThrow().getId();

        summaryRebuilder.rebuildAllSummaries();

        assertThat(patternStatsSummaryRepository
                .findByStatTypeAndBucketKey(WinningStatisticsCacheService.TYPE_ODD_COUNT, "3")
                .orElseThrow().getId()).isEqualTo(oddRowId);
        assertThat(companionPairSummaryRepository.findByBallAAndBallB(1, 2).orElseThrow().getId())
                .isEqualTo(pairId);
    }

    @Test
    @DisplayName("빈도 통계 조회 시 데이터가 없으면 재생성을 시도하는지 확인")
    void getFrequencyStats_fallsBackToRebuildWhenEmpty() {
        FrequencyStatsResponse response = service.getFrequencyStats();

        assertThat(response.totalRounds()).isEqualTo(2);
        assertThat(response.frequencies()).hasSize(45);
    }

    @Test
    @DisplayName("topSix/bottomSix에 1등 당첨 이력이 동봉된다")
    void getFrequencyStats_includesTopAndBottomSixWithWinHistory() {
        FrequencyStatsResponse response = service.getFrequencyStats();

        assertThat(response.topSix().balls()).hasSize(6);
        assertThat(response.bottomSix().balls()).hasSize(6);
        // 번호가 ballNumber 오름차순으로 정렬돼 내려오는지 확인(프론트가 그대로 렌더링할 수 있도록)
        assertThat(response.topSix().balls())
                .isSortedAccordingTo((a, b) -> Integer.compare(a.ballNumber(), b.ballNumber()));
        assertThat(response.bottomSix().balls())
                .isSortedAccordingTo((a, b) -> Integer.compare(a.ballNumber(), b.ballNumber()));
        assertThat(response.topSix().wonFirstPrize()).isTrue();
        assertThat(response.topSix().firstPrizeHistory())
                .extracting("round")
                .containsExactly(1);
        assertThat(response.bottomSix().wonFirstPrize()).isFalse();
        assertThat(response.bottomSix().firstPrizeHistory()).isEmpty();
    }

    @Test
    @DisplayName("limit 파라미터가 있어도 topSix/bottomSix가 계산된다")
    void getFrequencyStatsByLimit_includesTopAndBottomSix() {
        FrequencyStatsResponse response = service.getFrequencyStatsByLimit(1);

        assertThat(response.topSix().balls()).hasSize(6);
        assertThat(response.bottomSix().balls()).hasSize(6);
    }

    @Test
    @DisplayName("빈도 summary가 45개 미만으로 부분 손상돼도 완전히 재계산한다(T2)")
    void getFrequencyStats_partiallyCorruptedSummary_triggersFullRebuild() {
        summaryRebuilder.rebuildAllSummaries();
        assertThat(frequencySummaryRepository.findAll()).hasSize(45);

        // 45개 중 일부만 남기고 나머지를 지워 부분 손상 상태를 흉내낸다 — summaries가
        // "비어있지 않음"에만 의존하는 예전 로직이라면 이 상태를 정상으로 오인한다.
        List<FrequencySummary> all = frequencySummaryRepository.findAllByOrderByBallNumberAsc();
        frequencySummaryRepository.deleteAllInBatch(all.subList(0, 10));
        assertThat(frequencySummaryRepository.findAll()).hasSize(35);

        FrequencyStatsResponse response = service.getFrequencyStats();

        assertThat(response.frequencies()).hasSize(45);
    }

    @Test
    @DisplayName("H-01: 회차 데이터가 전혀 없어도 45개 전체 빈도가 0으로 채워진 완전 도메인을 반환하고 예외를 던지지 않는다")
    void getFrequencyStats_noWinningNumbers_returnsCompleteZeroFilledDomain() {
        winningNumberRepository.deleteAll();
        frequencySummaryRepository.deleteAll();

        FrequencyStatsResponse response = service.getFrequencyStats();

        // 예전에는 원본이 비면 summary도 통째로 비워서 frequencies가 0개였다 — 그러면
        // topSix/bottomSix를 구성할 6개가 없어 빈 그룹으로 대체됐다. H-01 이후에는
        // rebuilder가 항상 완전 도메인(45행)을 0으로 채우므로, 빈도가 전부 0이더라도
        // 6개씩 채워진 랭킹 그룹이 나와야 한다.
        assertThat(response.frequencies()).hasSize(45);
        assertThat(response.frequencies()).allSatisfy(f -> assertThat(f.frequency()).isZero());
        assertThat(response.topSix().balls()).hasSize(6);
        assertThat(response.bottomSix().balls()).hasSize(6);
    }

    @Test
    @DisplayName("제한 개수 지정 빈도 조회 시 최신 회차부터 지정 개수만 집계하는지 확인")
    void getFrequencyStatsByLimit_aggregatesOnlyLatestLimitRounds() {
        FrequencyStatsResponse response = service.getFrequencyStatsByLimit(1);

        // T1: totalRounds는 실제 반환된 표본 수(rounds.size()=1)여야 한다.
        // limit=1을 요청했을 뿐 최신 회차 번호(2)를 totalRounds로 쓰면 안 된다.
        assertThat(response.totalRounds()).isEqualTo(1);
        assertThat(response.frequencies()).hasSize(45);

        // limit=1이면 최신 회차(2: 1,2,10,20,30,45)만 집계 — 회차 1에만 있는 3,4,5,6은 포함되지 않는다
        response.frequencies().forEach(f -> {
            if (List.of(1, 2, 10, 20, 30, 45).contains(f.ballNumber())) {
                assertThat(f.frequency()).isEqualTo(1);
                assertThat(f.lastRound()).isEqualTo(2);
            } else if (List.of(3, 4, 5, 6).contains(f.ballNumber())) {
                assertThat(f.frequency()).isEqualTo(0);
            }
        });
    }

    @Test
    @DisplayName("중간 회차가 누락돼 최신 회차 번호와 실제 저장 회차 수가 다르면 totalRounds는 실제 저장 수를 반환한다")
    void getFrequencyStats_missingRoundInMiddle_totalRoundsReflectsActualCount() {
        // setUp에서 이미 회차 1, 2가 저장돼 있다. 회차 5를 추가해 3·4가 누락된 상태를 만든다 —
        // 최신 회차 번호는 5이지만 실제 저장된 회차 수는 3(1, 2, 5)이다.
        winningNumberRepository.save(round(5, 7, 8, 9, 10, 11, 12, 13));

        FrequencyStatsResponse response = service.getFrequencyStats();

        assertThat(response.totalRounds()).isEqualTo(3);
    }

    @Test
    @DisplayName("KB-16: rebuild 이후 새 회차가 수집되면(구조적으로는 여전히 완전) totalRounds는 실시간 count가 아니라 마지막 rebuild 시점 스냅숏을 반환한다")
    void getFrequencyStats_newRoundCollectedAfterRebuild_totalRoundsReflectsLastRebuildSnapshotNotLiveCount() {
        // setUp에 이미 회차 1, 2가 있다 — 여기서 명시적으로 rebuild해 summary/프로젝션
        // 상태를 "회차 2개" 스냅숏으로 고정한다.
        summaryRebuilder.rebuildAllSummaries();

        // rebuild 없이 회차를 하나 더 추가한다 — summary는 여전히 45개(구조적으로 완전)라
        // getFrequencyStats()의 불완전성 검사(size != 45)로는 이 staleness를 못 잡는다.
        // 예전 구현(winningNumberRepository.count())이라면 여기서 totalRounds=3이 나와
        // summary 내용(2개 회차 기준 빈도)과 분모(3)가 서로 다른 시점을 가리키게 된다.
        winningNumberRepository.save(round(3, 9, 10, 11, 12, 13, 14, 15));

        FrequencyStatsResponse response = service.getFrequencyStats();

        assertThat(response.totalRounds()).isEqualTo(2);
    }

    @Test
    @DisplayName("limit이 실제 저장된 회차 수보다 크면 totalRounds는 요청 limit이 아니라 실제 표본 수를 반환한다")
    void getFrequencyStatsByLimit_limitExceedsAvailableRounds_totalRoundsReflectsActualCount() {
        FrequencyStatsResponse response = service.getFrequencyStatsByLimit(500);

        // 저장된 회차는 2개뿐이므로 500을 요청해도 실제 표본은 2다.
        assertThat(response.totalRounds()).isEqualTo(2);
    }

    @Test
    @DisplayName("summary가 비어있으면 findMaxLastRound는 0을 반환한다")
    void findMaxLastRound_returnsZero_whenSummaryEmpty() {
        assertThat(frequencySummaryRepository.findMaxLastRound()).isZero();
    }

    @Test
    @DisplayName("재생성 후 findMaxLastRound는 최신 회차를 반환한다")
    void findMaxLastRound_returnsLatestRound_afterRebuild() {
        summaryRebuilder.rebuildAllSummaries();

        assertThat(frequencySummaryRepository.findMaxLastRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("번호 분석 시 메트릭이 정확하게 계산되는지 확인")
    void analyze_returnsCorrectMetrics() {
        AnalysisResponse result = service.analyze(List.of(1, 2, 3, 4, 5, 6));

        assertThat(result.oddCount()).isEqualTo(3);
        assertThat(result.evenCount()).isEqualTo(3);
        assertThat(result.sumOfNumbers()).isEqualTo(21);
        assertThat(result.sumBucket()).isEqualTo("21-65");
        assertThat(result.consecutivePairCount()).isEqualTo(5); // 1-2, 2-3, 3-4, 4-5, 5-6
        assertThat(result.lowCount()).isEqualTo(6); // 모두 1-22 범위
        assertThat(result.highCount()).isEqualTo(0);
        assertThat(result.wonFirstPrize()).isTrue();
        assertThat(result.firstPrizeHistory())
                .extracting("round")
                .containsExactly(1);
    }

    @Test
    @DisplayName("같은 번호 조합이 여러 번 1등이면 최신 회차부터 모든 내역을 반환한다")
    void analyze_returnsEveryMatchingFirstPrizeRoundNewestFirst() {
        winningNumberRepository.save(round(3, 1, 2, 3, 4, 5, 6, 8));

        AnalysisResponse result = service.analyze(List.of(6, 5, 4, 3, 2, 1));

        assertThat(result.wonFirstPrize()).isTrue();
        assertThat(result.firstPrizeHistory())
                .extracting("round")
                .containsExactly(3, 1);
    }

    @Test
    @DisplayName("범위 분산 조합(9,10,19,20,40,45)의 모든 분석 지표를 계산한다")
    void analyze_goldenFixture_rangeDistributionCombo() {
        AnalysisResponse result = service.analyze(List.of(9, 10, 19, 20, 40, 45));

        assertThat(result.oddCount()).isEqualTo(3);
        assertThat(result.evenCount()).isEqualTo(3);
        assertThat(result.lowCount()).isEqualTo(4);
        assertThat(result.highCount()).isEqualTo(2);
        assertThat(result.sumOfNumbers()).isEqualTo(143);
        assertThat(result.sumBucket()).isEqualTo("111-155");
        assertThat(result.consecutivePairCount()).isEqualTo(2); // 9-10, 19-20
        assertThat(result.rangeDistribution()).containsExactly(
                new AnalysisResponse.RangeDistribution("1-9", 1),
                new AnalysisResponse.RangeDistribution("10-19", 2),
                new AnalysisResponse.RangeDistribution("20-29", 1),
                new AnalysisResponse.RangeDistribution("30-39", 0),
                new AnalysisResponse.RangeDistribution("40-45", 2));
    }

    @Test
    @DisplayName("7의 배수 조합(7,14,21,28,35,42)의 모든 분석 지표를 계산한다")
    void analyze_goldenFixture_multiplesOfSevenCombo() {
        AnalysisResponse result = service.analyze(List.of(7, 14, 21, 28, 35, 42));

        assertThat(result.oddCount()).isEqualTo(3);
        assertThat(result.evenCount()).isEqualTo(3);
        assertThat(result.lowCount()).isEqualTo(3);
        assertThat(result.highCount()).isEqualTo(3);
        assertThat(result.sumOfNumbers()).isEqualTo(147);
        assertThat(result.sumBucket()).isEqualTo("111-155");
        assertThat(result.consecutivePairCount()).isEqualTo(0);
        assertThat(result.rangeDistribution()).containsExactly(
                new AnalysisResponse.RangeDistribution("1-9", 1),
                new AnalysisResponse.RangeDistribution("10-19", 1),
                new AnalysisResponse.RangeDistribution("20-29", 2),
                new AnalysisResponse.RangeDistribution("30-39", 1),
                new AnalysisResponse.RangeDistribution("40-45", 1));
    }

    private WinningNumber round(int r, int n1, int n2, int n3, int n4, int n5, int n6, int bonus) {
        return com.kraft.winningnumber.WinningNumberTestFactory.create(r, LocalDate.of(2026, 1, r),
                n1, n2, n3, n4, n5, n6, bonus,
                1_000_000_000L, 0L, 0, 0L, 0L,
                OffsetDateTime.now(Clock.system(KST)));
    }
}
