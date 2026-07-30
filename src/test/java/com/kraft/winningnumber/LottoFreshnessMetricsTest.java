package com.kraft.winningnumber;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("로또 최신성 지표 단위 테스트")
class LottoFreshnessMetricsTest {

    @Mock
    private WinningNumberRepository winningNumberRepository;

    @Mock
    private LottoDrawScheduleCalculator drawScheduleCalculator;

    @Test
    @DisplayName("최신 회차가 없으면 모든 최신성 지표는 0이어야 한다")
    void snapshot_returnsZeroesWhenNoWinningNumberExists() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneId.of("Asia/Seoul"));
        given(winningNumberRepository.findTopByOrderByRoundDesc()).willReturn(Optional.empty());

        LottoFreshnessMetrics metrics = new LottoFreshnessMetrics(
                new SimpleMeterRegistry(), winningNumberRepository, clock, drawScheduleCalculator);

        LottoFreshnessMetrics.FreshnessSnapshot snapshot = metrics.snapshot();
        assertThat(snapshot.latestRound()).isZero();
        assertThat(snapshot.expectedLatestRound()).isZero();
        assertThat(snapshot.staleDays()).isZero();
        assertThat(snapshot.dataPresent()).isZero();
    }

    @Test
    @DisplayName("최신 회차가 있으면 최신 회차와 예상 회차와 지연 일수를 한 번에 계산한다")
    void snapshot_returnsDerivedFreshnessValues() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneId.of("Asia/Seoul"));
        WinningNumber latest = WinningNumberTestFactory.create(
                1200,
                LocalDate.of(2026, 6, 13),
                3, 11, 19, 28, 34, 42,
                7,
                2_000_000_000L,
                0L, 0, 0L, 0L,
                ZonedDateTime.now(clock).toOffsetDateTime()
        );
        given(winningNumberRepository.findTopByOrderByRoundDesc()).willReturn(Optional.of(latest));
        given(drawScheduleCalculator.expectedLatestDrawDate(ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.of("Asia/Seoul"))))
                .willReturn(LocalDate.of(2026, 6, 20));

        LottoFreshnessMetrics metrics = new LottoFreshnessMetrics(
                new SimpleMeterRegistry(), winningNumberRepository, clock, drawScheduleCalculator);

        LottoFreshnessMetrics.FreshnessSnapshot snapshot = metrics.snapshot();
        assertThat(snapshot.latestRound()).isEqualTo(1200);
        assertThat(snapshot.expectedLatestRound()).isEqualTo(1201);
        assertThat(snapshot.staleDays()).isEqualTo(7);
        assertThat(snapshot.dataPresent()).isEqualTo(1);
    }

    @Test
    @DisplayName("kraft_lotto_winning_data_present gauge는 데이터 유무를 그대로 반영한다")
    void winningDataPresentGauge_reflectsRepositoryState() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneId.of("Asia/Seoul"));
        given(winningNumberRepository.findTopByOrderByRoundDesc()).willReturn(Optional.empty());
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        LottoFreshnessMetrics metrics = new LottoFreshnessMetrics(
                meterRegistry, winningNumberRepository, clock, drawScheduleCalculator);
        metrics.snapshot();

        assertThat(meterRegistry.get("kraft_lotto_winning_data_present").gauge().value()).isZero();
    }

    @Test
    @DisplayName("1초 이내 재호출은 캐시된 값을 반환하고 repository를 다시 조회하지 않는다")
    void snapshot_reusesCachedValueWithinTtl() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneId.of("Asia/Seoul"));
        given(winningNumberRepository.findTopByOrderByRoundDesc()).willReturn(Optional.empty());

        LottoFreshnessMetrics metrics = new LottoFreshnessMetrics(
                new SimpleMeterRegistry(), winningNumberRepository, clock, drawScheduleCalculator);

        metrics.snapshot();
        metrics.snapshot();
        metrics.snapshot();

        verify(winningNumberRepository, times(1)).findTopByOrderByRoundDesc();
    }
}
