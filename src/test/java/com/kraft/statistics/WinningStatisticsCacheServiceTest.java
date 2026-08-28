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
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("당첨 통계 캐시 서비스 테스트")
class WinningStatisticsCacheServiceTest {

    @Autowired
    private WinningStatisticsCacheService service;

    @Autowired
    private WinningNumberRepository winningNumberRepository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @BeforeEach
    void setUp() {
        winningNumberRepository.deleteAll();

        // 회차 1: 1, 2, 3, 4, 5, 6 (홀3·짝3, 저6·고0, 합21)
        winningNumberRepository.save(round(1, 1, 2, 3, 4, 5, 6, 7));
        // 회차 2: 1, 2, 10, 20, 30, 45 (홀2·짝4, 저3·고3, 합108)
        winningNumberRepository.save(round(2, 1, 2, 10, 20, 30, 45, 8));
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
