package com.kraft.winningnumber;

import com.kraft.common.error.ApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("당첨 번호 수집 서비스 테스트")
class WinningNumberCollectionServiceTest {

    @Test
    @DisplayName("최신 회차 수집 시 저장소의 마지막 회차 다음 번호를 사용하는지 확인")
    void collectLatestUsesNextRoundFromRepository() {
        WinningNumberRepository repository = mock(WinningNumberRepository.class);
        ExternalWinningNumberFetchClient fetchClient = mock(ExternalWinningNumberFetchClient.class);
        WinningNumberCommandService commandService = mock(WinningNumberCommandService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(new WinningNumber(
                1200,
                LocalDate.of(2026, 6, 13),
                3, 11, 19, 28, 34, 42,
                7,
                2_000_000_000L,
                0L, 0, 0L, 0L,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        )));

        WinningNumberUpsertRequest fetched = new WinningNumberUpsertRequest(
                1201,
                LocalDate.of(2026, 6, 20),
                java.util.List.of(5, 12, 18, 27, 36, 44),
                9,
                2_100_000_000L,
                null, null, null, null
        );
        when(fetchClient.fetchRound(1201)).thenReturn(fetched);
        WinningNumberResponse fetchedResponse = new WinningNumberResponse(
                1201,
                LocalDate.of(2026, 6, 20),
                java.util.List.of(5, 12, 18, 27, 36, 44),
                9,
                2_100_000_000L,
                0L, 0, 0L, 0L
        );
        when(commandService.upsertWithResult(fetched)).thenReturn(new WinningNumberUpsertResult(fetchedResponse, true));

        WinningNumberCollectionEventPublisher collectionEventPublisher = mock(WinningNumberCollectionEventPublisher.class);
        WinningNumberCollectionService service = new WinningNumberCollectionService(
                repository,
                fetchClient,
                commandService,
                eventPublisher,
                collectionEventPublisher,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                0
        );
        WinningNumberResponse response = service.collectLatest();

        ArgumentCaptor<Integer> roundCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(fetchClient).fetchRound(roundCaptor.capture());

        assertThat(roundCaptor.getValue()).isEqualTo(1201);
        assertThat(response.round()).isEqualTo(1201);
    }

    @Test
    @DisplayName("최신 다음 회차가 아직 없으면 기존 최신 회차를 반환하고 실패로 기록하지 않는다")
    void collectLatest_returnsExistingLatest_whenNextRoundNotPublishedYet() {
        WinningNumberRepository repository = mock(WinningNumberRepository.class);
        ExternalWinningNumberFetchClient fetchClient = mock(ExternalWinningNumberFetchClient.class);
        WinningNumberCommandService commandService = mock(WinningNumberCommandService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        WinningNumberCollectionEventPublisher collectionEventPublisher = mock(WinningNumberCollectionEventPublisher.class);

        WinningNumber latest = new WinningNumber(
                1200,
                LocalDate.of(2026, 6, 13),
                3, 11, 19, 28, 34, 42,
                7,
                2_000_000_000L,
                0L, 0, 0L, 0L,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        );
        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.of(latest));
        when(fetchClient.fetchRound(1201)).thenThrow(new ApiException(
                HttpStatus.BAD_GATEWAY,
                "LOTTO_SOURCE_ROUND_NOT_FOUND",
                "round not found"
        ));

        WinningNumberCollectionService service = new WinningNumberCollectionService(
                repository,
                fetchClient,
                commandService,
                eventPublisher,
                collectionEventPublisher,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                0
        );

        WinningNumberResponse response = service.collectLatest();

        assertThat(response.round()).isEqualTo(1200);

        ArgumentCaptor<WinningNumberCollectionLoggedEvent> loggedCaptor =
                ArgumentCaptor.forClass(WinningNumberCollectionLoggedEvent.class);
        verify(eventPublisher).publishEvent(loggedCaptor.capture());
        WinningNumberCollectionLoggedEvent logged = loggedCaptor.getValue();
        assertThat(logged.operationType()).isEqualTo(WinningNumberOperationType.EXTERNAL_COLLECT);
        assertThat(logged.success()).isTrue();
        assertThat(logged.round()).isEqualTo(1200);
        assertThat(logged.sourceDetail()).isEqualTo("external-source");
        assertThat(logged.message()).contains("이미 최신");
        verify(commandService, never()).upsertWithResult(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("다중 회차 따라잡기 시 회차별이 아닌 전체 완료 후 이벤트를 1회만 발행한다")
    void collectUpToLatest_publishesEventOnlyOnceAfterAllRounds() {
        WinningNumberRepository repository = mock(WinningNumberRepository.class);
        ExternalWinningNumberFetchClient fetchClient = mock(ExternalWinningNumberFetchClient.class);
        WinningNumberCommandService commandService = mock(WinningNumberCommandService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        WinningNumberCollectionEventPublisher collectionEventPublisher = mock(WinningNumberCollectionEventPublisher.class);

        WinningNumber round1200 = new WinningNumber(
                1200,
                LocalDate.of(2026, 6, 13),
                3, 11, 19, 28, 34, 42,
                7,
                2_000_000_000L,
                0L, 0, 0L, 0L,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        );
        WinningNumber round1201 = new WinningNumber(
                1201,
                LocalDate.of(2026, 6, 20),
                5, 12, 18, 27, 36, 44,
                9,
                2_100_000_000L,
                0L, 0, 0L, 0L,
                OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        );
        when(repository.findTopByOrderByRoundDesc())
                .thenReturn(Optional.of(round1200))
                .thenReturn(Optional.of(round1201));

        WinningNumberUpsertRequest fetched1201 = new WinningNumberUpsertRequest(
                1201, LocalDate.of(2026, 6, 20),
                java.util.List.of(5, 12, 18, 27, 36, 44), 9, 2_100_000_000L,
                null, null, null, null
        );
        WinningNumberUpsertRequest fetched1202 = new WinningNumberUpsertRequest(
                1202, LocalDate.of(2026, 6, 27),
                java.util.List.of(1, 8, 15, 22, 29, 36), 4, 2_200_000_000L,
                null, null, null, null
        );
        when(fetchClient.fetchRound(1201)).thenReturn(fetched1201);
        when(fetchClient.fetchRound(1202)).thenReturn(fetched1202);

        WinningNumberResponse response1201 = new WinningNumberResponse(
                1201, LocalDate.of(2026, 6, 20),
                java.util.List.of(5, 12, 18, 27, 36, 44), 9, 2_100_000_000L,
                0L, 0, 0L, 0L
        );
        WinningNumberResponse response1202 = new WinningNumberResponse(
                1202, LocalDate.of(2026, 6, 27),
                java.util.List.of(1, 8, 15, 22, 29, 36), 4, 2_200_000_000L,
                0L, 0, 0L, 0L
        );
        when(commandService.upsertWithResult(fetched1201)).thenReturn(new WinningNumberUpsertResult(response1201, true));
        when(commandService.upsertWithResult(fetched1202)).thenReturn(new WinningNumberUpsertResult(response1202, true));

        WinningNumberCollectionService service = new WinningNumberCollectionService(
                repository,
                fetchClient,
                commandService,
                eventPublisher,
                collectionEventPublisher,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                0
        );

        java.util.List<WinningNumberResponse> collected = service.collectUpToLatest(2);

        assertThat(collected).extracting(WinningNumberResponse::round).containsExactly(1201, 1202);

        ArgumentCaptor<WinningNumbersCollectedEvent> eventCaptor =
                ArgumentCaptor.forClass(WinningNumbersCollectedEvent.class);
        verify(collectionEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().round()).isEqualTo(1202);
        assertThat(eventCaptor.getValue().dataChanged()).isTrue();
    }

    @Test
    @DisplayName("catch-up 루프는 회차 사이에만 지연을 두고 마지막 회차 뒤에는 대기하지 않는다")
    void collectUpToLatest_sleepsBetweenRoundsButNotAfterTheLast() {
        WinningNumberRepository repository = mock(WinningNumberRepository.class);
        ExternalWinningNumberFetchClient fetchClient = mock(ExternalWinningNumberFetchClient.class);
        WinningNumberCommandService commandService = mock(WinningNumberCommandService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        WinningNumberCollectionEventPublisher collectionEventPublisher = mock(WinningNumberCollectionEventPublisher.class);

        when(repository.findTopByOrderByRoundDesc()).thenReturn(Optional.empty());
        for (int round = 1; round <= 2; round++) {
            WinningNumberUpsertRequest fetched = new WinningNumberUpsertRequest(
                    round, LocalDate.of(2026, 1, round),
                    java.util.List.of(1, 2, 3, 4, 5, 6), 7, 1_000_000_000L,
                    null, null, null, null
            );
            WinningNumberResponse response = new WinningNumberResponse(
                    round, LocalDate.of(2026, 1, round),
                    java.util.List.of(1, 2, 3, 4, 5, 6), 7, 1_000_000_000L,
                    0L, 0, 0L, 0L
            );
            when(fetchClient.fetchRound(round)).thenReturn(fetched);
            when(commandService.upsertWithResult(fetched)).thenReturn(new WinningNumberUpsertResult(response, true));
        }

        long delayMs = 150;
        WinningNumberCollectionService service = new WinningNumberCollectionService(
                repository, fetchClient, commandService, eventPublisher,
                collectionEventPublisher, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                delayMs
        );

        long start = System.nanoTime();
        service.collectUpToLatest(2);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 2회차면 지연이 정확히 1회(회차 사이) 있어야 한다 — 2회면 2배 이상 걸린다.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(delayMs);
        assertThat(elapsedMs).isLessThan(delayMs * 2);
    }
}
