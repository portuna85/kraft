package com.kraft.statistics;

import com.kraft.winningnumber.WinningNumber;
import com.kraft.winningnumber.WinningNumberRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("통계 재조정 스케줄러 단위 테스트")
class StatisticsReconciliationSchedulerTest {

    @Mock
    private FrequencySummaryRepository frequencySummaryRepository;
    @Mock
    private WinningNumberRepository winningNumberRepository;
    @Mock
    private StatisticsSummaryRebuilder summaryRebuilder;

    private StatisticsReconciliationScheduler scheduler;

    @Test
    @DisplayName("projected가 latest보다 뒤처지면 재조정을 호출한다")
    void reconcileIfBehind_projectedBehindLatest_triggersRebuild() {
        scheduler = new StatisticsReconciliationScheduler(
                frequencySummaryRepository, winningNumberRepository, summaryRebuilder);
        given(frequencySummaryRepository.findMaxLastRound()).willReturn(1229);
        given(winningNumberRepository.findTopByOrderByRoundDesc())
                .willReturn(Optional.of(winningNumber(1230)));

        scheduler.reconcileIfBehind();

        verify(summaryRebuilder).rebuildAllSummaries();
    }

    @Test
    @DisplayName("이미 최신이면 재조정을 호출하지 않는다")
    void reconcileIfBehind_alreadyUpToDate_doesNotTriggerRebuild() {
        scheduler = new StatisticsReconciliationScheduler(
                frequencySummaryRepository, winningNumberRepository, summaryRebuilder);
        given(frequencySummaryRepository.findMaxLastRound()).willReturn(1230);
        given(winningNumberRepository.findTopByOrderByRoundDesc())
                .willReturn(Optional.of(winningNumber(1230)));

        scheduler.reconcileIfBehind();

        verify(summaryRebuilder, never()).rebuildAllSummaries();
    }

    private WinningNumber winningNumber(int round) {
        return com.kraft.winningnumber.WinningNumberTestFactory.create(round, LocalDate.of(2026, 1, 1),
                1, 2, 3, 4, 5, 6, 7,
                1_000_000_000L, 0L, 0, 0L, 0L,
                OffsetDateTime.now());
    }
}
