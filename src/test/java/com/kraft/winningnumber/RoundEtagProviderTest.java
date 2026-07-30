package com.kraft.winningnumber;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("회차 이태그 제공자 테스트")
class RoundEtagProviderTest {

    @Test
    @DisplayName("최신성 경로는 회차가 알려진 상태에서도 항상 폴백 이태그를 사용한다")
    void freshnessPath_alwaysReturnsNullEvenWithKnownRound() {
        RoundEtagProvider provider = providerWithLatestRound(1234);

        assertThat(provider.etagForPath("/api/v1/rounds/freshness")).isNull();
    }

    @Test
    @DisplayName("장애 이력 경로는 회차가 알려진 상태에서도 항상 폴백 이태그를 사용한다")
    void incidentsPath_alwaysReturnsNullEvenWithKnownRound() {
        RoundEtagProvider provider = providerWithLatestRound(1234);

        assertThat(provider.etagForPath("/api/v1/status/incidents")).isNull();
    }

    @Test
    @DisplayName("회차를 아직 모르는 상태에서도 특수 경로는 폴백 이태그를 사용한다")
    void unknownRoundState_stillReturnsNullForSpecialPaths() {
        RoundEtagProvider provider = providerWithNoRound();

        assertThat(provider.etagForPath("/api/v1/rounds/freshness")).isNull();
        assertThat(provider.etagForPath("/api/v1/status/incidents")).isNull();
    }

    @Test
    @DisplayName("그 외 경로는 최신 회차 기반 이태그를 반환한다")
    void otherNonSpecialPath_stillReturnsMutableRoundEtag() {
        RoundEtagProvider provider = providerWithLatestRound(1234);

        assertThat(provider.etagForPath("/api/v1/rounds/latest")).startsWith("\"round-1234-b");
    }

    @Test
    @DisplayName("과거 회차 재수집 이벤트가 발생해도 mutableETag는 과거 값으로 회귀하지 않는다")
    void onCollected_pastRoundRecollection_doesNotRegressMutableEtag() {
        WinningNumberRepository repository = mock(WinningNumberRepository.class);
        WinningNumber latest = winningNumber(1234);
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(latest));
        RoundEtagProvider provider = new RoundEtagProvider(repository);
        provider.init();

        String beforeRegression = provider.etagForPath("/api/v1/rounds/latest");

        // 과거 회차(1200) 재수집 이벤트 — 최신 회차는 여전히 1234이므로 리스너가 조회한 최신값이 쓰인다
        provider.onCollected(new WinningNumbersCollectedEvent(1200, true));

        String afterOldRoundEvent = provider.etagForPath("/api/v1/rounds/latest");
        assertThat(afterOldRoundEvent).startsWith("\"round-1234-b");
        assertThat(afterOldRoundEvent).isNotEqualTo(beforeRegression);
        assertThat(afterOldRoundEvent).doesNotContain("round-1200");
    }

    private static WinningNumber winningNumber(int round) {
        return WinningNumberTestFactory.create(
                round,
                LocalDate.of(2002, 12, 7),
                10, 23, 29, 33, 37, 40,
                16,
                857_956_000L,
                0L, 0, 0L, 0L,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        );
    }

    private static RoundEtagProvider providerWithLatestRound(int round) {
        WinningNumberRepository repository = mock(WinningNumberRepository.class);
        WinningNumber winningNumber = WinningNumberTestFactory.create(
                round,
                LocalDate.of(2002, 12, 7),
                10, 23, 29, 33, 37, 40,
                16,
                857_956_000L,
                0L, 0, 0L, 0L,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        );
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(winningNumber));
        RoundEtagProvider provider = new RoundEtagProvider(repository);
        provider.init();
        return provider;
    }

    private static RoundEtagProvider providerWithNoRound() {
        WinningNumberRepository repository = mock(WinningNumberRepository.class);
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.empty());
        RoundEtagProvider provider = new RoundEtagProvider(repository);
        provider.init();
        return provider;
    }
}
