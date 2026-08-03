package com.kraft.winningnumber;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("당첨 번호 백필 서비스 단위 테스트")
class WinningNumberBackfillServiceTest {

    @Mock
    private WinningNumberRepository repository;

    @Mock
    private ExternalWinningNumberFetchClient fetchClient;

    @Mock
    private WinningNumberCommandService commandService;

    @Mock
    private WinningNumberCollectionService collectionService;

    private WinningNumberBackfillService service;

    @BeforeEach
    void setUp() {
        service = new WinningNumberBackfillService(
                repository,
                fetchClient,
                commandService,
                collectionService,
                0,
                0,
                2
        );
    }

    @Test
    @DisplayName("데이터베이스가 비어 있으면 1회차부터 데이터가 없을 때까지 백필을 진행한다")
    void backfillAll_emptyDb_collectsUntilRoundNotFound() {
        given(repository.findAllRoundsOrderByRoundAsc()).willReturn(List.of());
        given(fetchClient.fetchRound(1)).willReturn(request(1));
        given(fetchClient.fetchRound(2)).willReturn(request(2));
        given(fetchClient.fetchRound(3)).willReturn(request(3));
        given(fetchClient.fetchRound(4)).willThrow(sourceException(ApiErrorCode.LOTTO_SOURCE_ROUND_NOT_FOUND));
        given(commandService.upsertWithResult(request(1))).willReturn(changedResult());
        given(commandService.upsertWithResult(request(2))).willReturn(changedResult());
        given(commandService.upsertWithResult(request(3))).willReturn(changedResult());

        BackfillResult result = service.backfillAll();

        assertThat(result.startRound()).isEqualTo(1);
        assertThat(result.lastCollectedRound()).isEqualTo(3);
        assertThat(result.collectedCount()).isEqualTo(3);
        assertThat(result.updatedCount()).isEqualTo(3);
        verify(collectionService).publishBulkCollected(3);
    }

    @Test
    @DisplayName("연속된 데이터베이스 데이터가 있는 경우 다음 회차부터 시작하며 수집된 것이 없으면 이벤트를 발행하지 않는다")
    void backfillAll_continuousDbStopsImmediatelyWithoutPublishing() {
        given(repository.findAllRoundsOrderByRoundAsc()).willReturn(
                java.util.stream.IntStream.rangeClosed(1, 500).boxed().toList()
        );
        given(fetchClient.fetchRound(501)).willThrow(sourceException(ApiErrorCode.LOTTO_SOURCE_ROUND_NOT_FOUND));

        BackfillResult result = service.backfillAll();

        assertThat(result.startRound()).isEqualTo(501);
        assertThat(result.lastCollectedRound()).isNull();
        assertThat(result.collectedCount()).isZero();
        assertThat(result.updatedCount()).isZero();
        verify(collectionService, never()).publishBulkCollected(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("데이터베이스에 누락된 회차가 있으면 첫 번째 누락된 회차부터 시작한다")
    void backfillAll_sparseDbStartsFromFirstMissingRound() {
        given(repository.findAllRoundsOrderByRoundAsc()).willReturn(List.of(1, 2, 4, 1228));
        given(fetchClient.fetchRound(3)).willReturn(request(3));
        given(fetchClient.fetchRound(4)).willThrow(sourceException(ApiErrorCode.LOTTO_SOURCE_ROUND_NOT_FOUND));
        given(commandService.upsertWithResult(request(3))).willReturn(changedResult());

        BackfillResult result = service.backfillAll();

        assertThat(result.startRound()).isEqualTo(3);
        assertThat(result.lastCollectedRound()).isEqualTo(3);
        assertThat(result.collectedCount()).isEqualTo(1);
        verify(fetchClient).fetchRound(3);
        verify(collectionService).publishBulkCollected(3);
    }

    @Test
    @DisplayName("일시적인 소스 오류 발생 시 재시도 후 수집을 진행한다")
    void backfillAll_transientErrorRetriesSameRoundAndCollects() {
        given(repository.findAllRoundsOrderByRoundAsc()).willReturn(List.of());
        given(fetchClient.fetchRound(1))
                .willThrow(sourceException(ApiErrorCode.LOTTO_SOURCE_CIRCUIT_OPEN))
                .willReturn(request(1));
        given(fetchClient.fetchRound(2)).willThrow(sourceException(ApiErrorCode.LOTTO_SOURCE_ROUND_NOT_FOUND));
        given(commandService.upsertWithResult(request(1))).willReturn(changedResult());

        BackfillResult result = service.backfillAll();

        assertThat(result.startRound()).isEqualTo(1);
        assertThat(result.lastCollectedRound()).isEqualTo(1);
        assertThat(result.collectedCount()).isEqualTo(1);
        verify(fetchClient, times(2)).fetchRound(1);
        verify(commandService).upsertWithResult(request(1));
        verify(collectionService).publishBulkCollected(1);
    }

    @Test
    @DisplayName("일시적 소스 오류가 최대 재시도 횟수를 초과하면 중단한다")
    void backfillAll_transientErrorsStopAfterMaxRetriesExceeded() {
        RuntimeException temporary = new ApiException(
                ApiErrorCode.LOTTO_SOURCE_CIRCUIT_OPEN,
                "temporary source failure"
        );
        given(repository.findAllRoundsOrderByRoundAsc()).willReturn(List.of());
        given(fetchClient.fetchRound(1)).willThrow(temporary);

        BackfillResult result = service.backfillAll();

        assertThat(result.startRound()).isEqualTo(1);
        assertThat(result.lastCollectedRound()).isNull();
        assertThat(result.collectedCount()).isZero();
        assertThat(result.stopReason()).contains("1", "3", "temporary source failure");
        verify(fetchClient, times(3)).fetchRound(1);
        verify(commandService, never()).upsertWithResult(org.mockito.ArgumentMatchers.any());
        verify(collectionService, never()).publishBulkCollected(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("소스가 비활성화된 경우 즉시 중단한다")
    void backfillAll_disabledSourceStopsImmediately() {
        given(repository.findAllRoundsOrderByRoundAsc()).willReturn(List.of());
        given(fetchClient.fetchRound(1)).willThrow(new ApiException(
                ApiErrorCode.LOTTO_SOURCE_DISABLED,
                "source disabled"
        ));

        BackfillResult result = service.backfillAll();

        assertThat(result.startRound()).isEqualTo(1);
        assertThat(result.lastCollectedRound()).isNull();
        assertThat(result.collectedCount()).isZero();
        assertThat(result.stopReason()).contains("1", "source disabled");
        verify(fetchClient).fetchRound(1);
        verify(collectionService, never()).publishBulkCollected(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("LOTTO_SOURCE_VALIDATION_ERROR는 재시도 없이 즉시 중단된다")
    void backfillAll_validationError_stopsImmediately() {
        given(repository.findAllRoundsOrderByRoundAsc()).willReturn(List.of());
        given(fetchClient.fetchRound(1)).willThrow(sourceException(ApiErrorCode.LOTTO_SOURCE_VALIDATION_ERROR));

        BackfillResult result = service.backfillAll();

        assertThat(result.collectedCount()).isZero();
        verify(fetchClient, times(1)).fetchRound(1);
        verify(collectionService, never()).publishBulkCollected(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("필드 파싱 실패는 데이터의 끝이 아닌 일시 오류로 간주해 재시도 후 중단한다")
    void backfillAll_parseErrorIsTransientNotEndOfData() {
        given(repository.findAllRoundsOrderByRoundAsc()).willReturn(List.of());
        given(fetchClient.fetchRound(1)).willThrow(sourceException(ApiErrorCode.LOTTO_SOURCE_PARSE_ERROR));

        BackfillResult result = service.backfillAll();

        assertThat(result.startRound()).isEqualTo(1);
        assertThat(result.lastCollectedRound()).isNull();
        assertThat(result.collectedCount()).isZero();
        assertThat(result.stopReason()).contains("연속 실패");
        verify(fetchClient, times(3)).fetchRound(1);
        verify(commandService, never()).upsertWithResult(org.mockito.ArgumentMatchers.any());
        verify(collectionService, never()).publishBulkCollected(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("동시 tryStart 호출 중 정확히 하나만 성공한다")
    void tryStart_concurrentCalls_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        try {
            List<? extends Future<?>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executor.submit(() -> {
                        ready.countDown();
                        try {
                            go.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        if (service.tryStart()) {
                            successCount.incrementAndGet();
                        }
                    }))
                    .toList();

            ready.await();
            go.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdownNow();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(service.isRunning()).isTrue();
    }

    @Test
    @DisplayName("완료 후(releaseStart)에는 다시 시작 예약이 가능하다")
    void tryStart_afterRelease_canStartAgain() {
        assertThat(service.tryStart()).isTrue();
        assertThat(service.tryStart()).isFalse();

        service.releaseStart();

        assertThat(service.tryStart()).isTrue();
    }

    private WinningNumberUpsertRequest request(int round) {
        return new WinningNumberUpsertRequest(
                round,
                LocalDate.of(2026, 1, 1).plusWeeks(round - 1L),
                List.of(1, 2, 3, 4, 5, 6),
                7,
                1_000_000_000L,
                null,
                null,
                null,
                null
        );
    }

    private WinningNumberUpsertResult changedResult() {
        return new WinningNumberUpsertResult(null, true);
    }

    private RuntimeException sourceException(ApiErrorCode errorCode) {
        return new ApiException(errorCode, errorCode.name());
    }
}
