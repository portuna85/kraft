package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.RecommendationImportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationAutoFetchSchedulerTest {

    @Mock
    private DhLotteryClient dhLotteryClient;
    @Mock
    private RecommendationHistoryImporter importer;
    @Mock
    private RecommendationHistoryStateRepository stateRepository;

    private RecommendationAutoFetchScheduler scheduler() {
        RecommendationAutoFetchScheduler scheduler =
                new RecommendationAutoFetchScheduler(dhLotteryClient, importer, stateRepository);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        return scheduler;
    }

    @Test
    @DisplayName("다음 회차 조회에 성공하면 importer로 반영하고, 그다음 회차가 아직 추첨 전이면 멈춘다")
    void success_importsNextRound() {
        given(stateRepository.findById(1)).willReturn(Optional.of(
                RecommendationHistoryState.builder().id(1).version(3L).verifiedThroughRound(10).build()));
        ImportedDraw draw = new ImportedDraw(11, List.of(1, 2, 3, 4, 5, 6));
        given(dhLotteryClient.fetchRound(11)).willReturn(new DhLotteryClient.FetchOutcome.Success(draw));
        given(importer.importHistory(eq(List.of(draw)), eq(11), eq("dhlottery-api-auto")))
                .willReturn(new RecommendationHistoryImporter.Result(1, 0, 11));
        given(dhLotteryClient.fetchRound(12)).willReturn(new DhLotteryClient.FetchOutcome.NotYetDrawn());

        scheduler().fetchLatestIfDue();

        verify(importer).importHistory(List.of(draw), 11, "dhlottery-api-auto");
    }

    /**
     * B14: 앱이 여러 주 내려가 있었다면 검증 구간이 여러 회차 뒤처질 수 있다. 예전에는
     * 실행 한 번에 회차 하나만 시도해, 밀린 만큼 따라잡는 데 그만큼의 예약 주기가 그대로
     * 걸렸다. 이제 한 번의 실행 안에서 연속으로 성공하는 한 계속 다음 회차를 이어서 시도한다.
     */
    @Test
    @DisplayName("B14: 연속으로 성공하는 동안은 한 번의 실행에서 여러 회차를 이어서 따라잡는다")
    void catchesUpMultipleRoundsInOneRun_untilNotYetDrawn() {
        given(stateRepository.findById(1)).willReturn(Optional.of(
                RecommendationHistoryState.builder().id(1).version(3L).verifiedThroughRound(10).build()));
        ImportedDraw draw11 = new ImportedDraw(11, List.of(1, 2, 3, 4, 5, 6));
        ImportedDraw draw12 = new ImportedDraw(12, List.of(7, 8, 9, 10, 11, 12));
        ImportedDraw draw13 = new ImportedDraw(13, List.of(13, 14, 15, 16, 17, 18));
        given(dhLotteryClient.fetchRound(11)).willReturn(new DhLotteryClient.FetchOutcome.Success(draw11));
        given(dhLotteryClient.fetchRound(12)).willReturn(new DhLotteryClient.FetchOutcome.Success(draw12));
        given(dhLotteryClient.fetchRound(13)).willReturn(new DhLotteryClient.FetchOutcome.Success(draw13));
        given(dhLotteryClient.fetchRound(14)).willReturn(new DhLotteryClient.FetchOutcome.NotYetDrawn());
        given(importer.importHistory(any(), anyInt(), eq("dhlottery-api-auto")))
                .willReturn(new RecommendationHistoryImporter.Result(1, 0, 0));

        scheduler().fetchLatestIfDue();

        verify(importer).importHistory(List.of(draw11), 11, "dhlottery-api-auto");
        verify(importer).importHistory(List.of(draw12), 12, "dhlottery-api-auto");
        verify(importer).importHistory(List.of(draw13), 13, "dhlottery-api-auto");
        verify(dhLotteryClient).fetchRound(14);
    }

    /**
     * B14: 한 번에 과도한 요청을 보내지 않도록 연속 시도 횟수에 상한을 둔다 — 모든 회차가
     * 계속 성공하더라도 이 상한을 넘어서는 요청은 다음 예약 실행이 이어받는다.
     */
    @Test
    @DisplayName("B14: 연속 성공이 계속돼도 한 실행에서 시도하는 회차 수는 상한을 넘지 않는다")
    void catchUp_stopsAtMaxRoundsPerRunEvenIfAllSucceed() {
        given(stateRepository.findById(1)).willReturn(Optional.of(
                RecommendationHistoryState.builder().id(1).version(3L).verifiedThroughRound(10).build()));
        given(dhLotteryClient.fetchRound(anyInt())).willAnswer(invocation -> {
            int round = invocation.getArgument(0);
            return new DhLotteryClient.FetchOutcome.Success(new ImportedDraw(round, List.of(1, 2, 3, 4, 5, 6)));
        });
        given(importer.importHistory(any(), anyInt(), eq("dhlottery-api-auto")))
                .willReturn(new RecommendationHistoryImporter.Result(1, 0, 0));

        scheduler().fetchLatestIfDue();

        // 11..20회차(10개)까지만 시도하고, 그다음(21)은 이번 실행에서 건드리지 않는다.
        verify(dhLotteryClient, org.mockito.Mockito.times(10)).fetchRound(anyInt());
        verify(dhLotteryClient, never()).fetchRound(21);
    }

    @Test
    @DisplayName("아직 추첨 전이면 importer를 호출하지 않는다")
    void notYetDrawn_doesNotImport() {
        given(stateRepository.findById(1)).willReturn(Optional.of(
                RecommendationHistoryState.builder().id(1).version(3L).verifiedThroughRound(10).build()));
        given(dhLotteryClient.fetchRound(11)).willReturn(new DhLotteryClient.FetchOutcome.NotYetDrawn());

        scheduler().fetchLatestIfDue();

        verify(importer, never()).importHistory(any(), anyInt(), any());
    }

    @Test
    @DisplayName("신뢰할 수 없는 응답이면 importer를 호출하지 않는다")
    void unavailable_doesNotImport() {
        given(stateRepository.findById(1)).willReturn(Optional.of(
                RecommendationHistoryState.builder().id(1).version(3L).verifiedThroughRound(10).build()));
        given(dhLotteryClient.fetchRound(11)).willReturn(new DhLotteryClient.FetchOutcome.Unavailable("HTTP_ERROR: boom"));

        scheduler().fetchLatestIfDue();

        verify(importer, never()).importHistory(any(), anyInt(), any());
    }

    @Test
    @DisplayName("이력이 아예 없으면 1회차부터 조회한다")
    void emptyHistory_fetchesRoundOne() {
        given(stateRepository.findById(1)).willReturn(Optional.empty());
        given(dhLotteryClient.fetchRound(1)).willReturn(new DhLotteryClient.FetchOutcome.NotYetDrawn());

        scheduler().fetchLatestIfDue();

        verify(dhLotteryClient).fetchRound(1);
    }

    @Test
    @DisplayName("스케줄러가 꺼져 있으면 아무 조회도 하지 않는다")
    void disabled_doesNothing() {
        RecommendationAutoFetchScheduler scheduler =
                new RecommendationAutoFetchScheduler(dhLotteryClient, importer, stateRepository);
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.fetchLatestIfDue();

        verify(dhLotteryClient, never()).fetchRound(anyInt());
    }

    @Test
    @DisplayName("반영 검증에 실패해도 예외를 전파하지 않는다(다음 예약 시각 재시도)")
    void importValidationFailure_isSwallowed() {
        given(stateRepository.findById(1)).willReturn(Optional.of(
                RecommendationHistoryState.builder().id(1).version(3L).verifiedThroughRound(10).build()));
        ImportedDraw draw = new ImportedDraw(11, List.of(1, 2, 3, 4, 5, 6));
        given(dhLotteryClient.fetchRound(11)).willReturn(new DhLotteryClient.FetchOutcome.Success(draw));
        given(importer.importHistory(any(), anyInt(), any()))
                .willThrow(new RecommendationImportException("MISSING_ROUND", "boom"));

        scheduler().fetchLatestIfDue();
    }
}
