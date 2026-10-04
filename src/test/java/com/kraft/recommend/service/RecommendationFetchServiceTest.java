package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationFetchAttempt;
import com.kraft.recommend.domain.RecommendationFetchAttempt.Outcome;
import com.kraft.recommend.domain.RecommendationFetchAttempt.Trigger;
import com.kraft.recommend.domain.RecommendationFetchAttemptRepository;
import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.service.RecommendationFetchService.RunResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 수집 실행의 시도 기록·재시작 복원·동시 실행 방지. 가져오는 규칙 자체는 RecommendationAutoFetchSchedulerTest가 본다. */
@ExtendWith(MockitoExtension.class)
class RecommendationFetchServiceTest {

    @Mock
    private DhLotteryClient dhLotteryClient;
    @Mock
    private RecommendationHistoryImporter importer;
    @Mock
    private RecommendationHistoryStateRepository stateRepository;
    @Mock
    private RecommendationFetchAttemptRepository attemptRepository;

    private final RecommendationFetchStatus fetchStatus = new RecommendationFetchStatus();

    private RecommendationFetchService service() {
        return new RecommendationFetchService(dhLotteryClient, importer, stateRepository, fetchStatus, attemptRepository);
    }

    private void givenVerifiedThrough(int round) {
        given(stateRepository.findById(1)).willReturn(Optional.of(
                RecommendationHistoryState.builder().id(1).version(1L).verifiedThroughRound(round).build()));
    }

    private static RecommendationFetchAttempt attempt(Outcome outcome, String detail, LocalDateTime at) {
        return RecommendationFetchAttempt.builder()
                .attemptedAt(at).trigger(Trigger.SCHEDULED).outcome(outcome).roundNo(11).detail(detail).build();
    }

    @Test
    @DisplayName("회차를 반영하면 FETCHED, 그다음 아직 추첨 전이면 NOT_YET_DRAWN으로 각각 기록하고 수동 출처를 남긴다")
    void recordsEachAttempt_andUsesManualSource() {
        givenVerifiedThrough(10);
        ImportedDraw draw = new ImportedDraw(11, List.of(1, 2, 3, 4, 5, 6));
        given(dhLotteryClient.fetchRound(11)).willReturn(new DhLotteryClient.FetchOutcome.Success(draw));
        given(dhLotteryClient.fetchRound(12)).willReturn(new DhLotteryClient.FetchOutcome.NotYetDrawn());
        given(importer.importHistory(any(), anyInt(), any()))
                .willReturn(new RecommendationHistoryImporter.Result(1, 0, 11));

        RunResult result = service().run(Trigger.MANUAL);

        assertThat(result.status()).isEqualTo(RunResult.Status.DONE);
        assertThat(result.fetchedRounds()).containsExactly(11);
        verify(importer).importHistory(List.of(draw), 11, "dhlottery-api-manual");
        ArgumentCaptor<RecommendationFetchAttempt> saved = ArgumentCaptor.forClass(RecommendationFetchAttempt.class);
        verify(attemptRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(RecommendationFetchAttempt::getOutcome)
                .containsExactly(Outcome.FETCHED, Outcome.NOT_YET_DRAWN);
        assertThat(saved.getAllValues()).extracting(RecommendationFetchAttempt::getTrigger)
                .containsOnly(Trigger.MANUAL);
    }

    @Test
    @DisplayName("신뢰할 수 없는 응답이면 FAILED로 사유와 함께 기록하고 연속 실패를 센다")
    void failure_isRecordedWithReason() {
        givenVerifiedThrough(10);
        given(dhLotteryClient.fetchRound(11)).willReturn(new DhLotteryClient.FetchOutcome.Unavailable("HTTP_ERROR: 500"));

        RunResult result = service().run(Trigger.SCHEDULED);

        assertThat(result.status()).isEqualTo(RunResult.Status.FAILED);
        assertThat(result.message()).contains("HTTP_ERROR: 500");
        assertThat(fetchStatus.consecutiveFailures()).isEqualTo(1);
        ArgumentCaptor<RecommendationFetchAttempt> saved = ArgumentCaptor.forClass(RecommendationFetchAttempt.class);
        verify(attemptRepository).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(saved.getValue().getDetail()).isEqualTo("HTTP_ERROR: 500");
        assertThat(saved.getValue().getRoundNo()).isEqualTo(11);
    }

    @Test
    @DisplayName("시도 기록을 저장하지 못해도 수집 결과는 그대로 돌려준다")
    void attemptSaveFailure_doesNotBreakTheRun() {
        givenVerifiedThrough(10);
        given(dhLotteryClient.fetchRound(11)).willReturn(new DhLotteryClient.FetchOutcome.NotYetDrawn());
        given(attemptRepository.save(any())).willThrow(new IllegalStateException("db down"));

        RunResult result = service().run(Trigger.SCHEDULED);

        assertThat(result.status()).isEqualTo(RunResult.Status.DONE);
    }

    @Test
    @DisplayName("이미 수집이 돌고 있으면 새 실행은 아무것도 조회하지 않고 BUSY를 돌려준다")
    void concurrentRun_isRejectedAsBusy() throws Exception {
        givenVerifiedThrough(10);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(dhLotteryClient.fetchRound(11)).willAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return new DhLotteryClient.FetchOutcome.NotYetDrawn();
        });

        RecommendationFetchService service = service();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<RunResult> first = executor.submit(() -> service.run(Trigger.SCHEDULED));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            RunResult second = service.run(Trigger.MANUAL);

            assertThat(second.status()).isEqualTo(RunResult.Status.BUSY);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(RunResult.Status.DONE);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        verify(dhLotteryClient, times(1)).fetchRound(anyInt());
        verify(dhLotteryClient, never()).fetchRound(eq(12));
    }

    @Test
    @DisplayName("재시작 뒤에는 시도 기록으로 마지막 성공·실패와 연속 실패 횟수를 되돌린다")
    void restoreFrom_rebuildsStatusFromAttempts() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 4, 7, 0);
        // 최근 것이 앞: 실패 2번 연속, 그 앞에 성공, 그보다 오래된 실패.
        List<RecommendationFetchAttempt> newestFirst = List.of(
                attempt(Outcome.FAILED, "HTTP_ERROR: B", base),
                attempt(Outcome.FAILED, "HTTP_ERROR: A", base.minusHours(1)),
                attempt(Outcome.NOT_YET_DRAWN, null, base.minusHours(2)),
                attempt(Outcome.FAILED, "OLD", base.minusDays(1)));

        service().restoreFrom(newestFirst);

        assertThat(fetchStatus.consecutiveFailures()).isEqualTo(2);
        assertThat(fetchStatus.lastFailureReason()).isEqualTo("HTTP_ERROR: B");
        assertThat(fetchStatus.lastSuccessAt()).isNotNull();
        assertThat(fetchStatus.lastFailureAt()).isAfter(fetchStatus.lastSuccessAt());
    }

    @Test
    @DisplayName("기록이 없으면 빈 상태로 복원한다")
    void restoreFrom_emptyHistory() {
        service().restoreFrom(List.of());

        assertThat(fetchStatus.consecutiveFailures()).isZero();
        assertThat(fetchStatus.lastSuccessAt()).isNull();
        assertThat(fetchStatus.lastFailureAt()).isNull();
    }
}
