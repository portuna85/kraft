package com.kraft.operations.recommendbackfill;

import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.service.DhLotteryClient;
import com.kraft.recommend.service.ImportedDraw;
import com.kraft.recommend.service.RecommendationHistoryImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * {@code chunk-size}만큼 모아 커밋하고, 도중에 막히면 이미 커밋된 청크는 남긴 채 멈추는지
 * 확인한다. {@link RecommendationBackfillRunner#backfill()}(package-private)을 직접 호출해
 * {@code run()}이 감싸는 {@code System.exit}를 거치지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationBackfillRunnerTest {

    @Mock
    private DhLotteryClient dhLotteryClient;
    @Mock
    private RecommendationHistoryImporter importer;
    @Mock
    private RecommendationHistoryStateRepository stateRepository;
    @Mock
    private ConfigurableApplicationContext context;

    private RecommendationBackfillRunner runner(int upToRound, int chunkSize) {
        RecommendationBackfillRunner runner =
                new RecommendationBackfillRunner(dhLotteryClient, importer, stateRepository, context);
        ReflectionTestUtils.setField(runner, "upToRound", upToRound);
        ReflectionTestUtils.setField(runner, "chunkSize", chunkSize);
        ReflectionTestUtils.setField(runner, "requestDelayMs", 0L);
        ReflectionTestUtils.setField(runner, "dryRun", false);
        return runner;
    }

    private DhLotteryClient.FetchOutcome successOf(int round) {
        return new DhLotteryClient.FetchOutcome.Success(new ImportedDraw(round, List.of(1, 2, 3, 4, 5, 6)));
    }

    @Test
    @DisplayName("chunk-size 단위로 커밋하고, 중간에 막히면 이미 채운 청크까지만 반영한 뒤 종료코드 1을 남긴다")
    void stopsAtFailureButKeepsCommittedChunks() {
        given(stateRepository.findById(1)).willReturn(Optional.empty());
        given(dhLotteryClient.fetchRound(1)).willReturn(successOf(1));
        given(dhLotteryClient.fetchRound(2)).willReturn(successOf(2));
        given(dhLotteryClient.fetchRound(3)).willReturn(successOf(3));
        given(dhLotteryClient.fetchRound(4)).willReturn(successOf(4));
        given(dhLotteryClient.fetchRound(5)).willReturn(new DhLotteryClient.FetchOutcome.NotYetDrawn());
        given(importer.importHistory(any(), anyInt(), any()))
                .willReturn(new RecommendationHistoryImporter.Result(2, 0, 0));

        int exitCode = runner(5, 2).backfill();

        assertThat(exitCode).isEqualTo(1);
        // 5회차까지 요청했지만 4회차까지만 청크(2개씩) 커밋되고 5회차에서 멈췄다.
        verify(importer).importHistory(
                List.of(new ImportedDraw(1, List.of(1, 2, 3, 4, 5, 6)), new ImportedDraw(2, List.of(1, 2, 3, 4, 5, 6))),
                2, "dhlottery-api-backfill");
        verify(importer).importHistory(
                List.of(new ImportedDraw(3, List.of(1, 2, 3, 4, 5, 6)), new ImportedDraw(4, List.of(1, 2, 3, 4, 5, 6))),
                4, "dhlottery-api-backfill");
    }

    @Test
    @DisplayName("요청 상한까지 모두 성공하면 종료코드 0을 남긴다")
    void allSucceed_exitsZero() {
        given(stateRepository.findById(1)).willReturn(Optional.empty());
        given(dhLotteryClient.fetchRound(1)).willReturn(successOf(1));
        given(dhLotteryClient.fetchRound(2)).willReturn(successOf(2));
        given(dhLotteryClient.fetchRound(3)).willReturn(successOf(3));
        given(importer.importHistory(any(), anyInt(), any()))
                .willReturn(new RecommendationHistoryImporter.Result(3, 0, 0));

        int exitCode = runner(3, 10).backfill();

        assertThat(exitCode).isEqualTo(0);
        verify(importer).importHistory(
                List.of(new ImportedDraw(1, List.of(1, 2, 3, 4, 5, 6)),
                        new ImportedDraw(2, List.of(1, 2, 3, 4, 5, 6)),
                        new ImportedDraw(3, List.of(1, 2, 3, 4, 5, 6))),
                3, "dhlottery-api-backfill");
    }

    @Test
    @DisplayName("이미 요청 상한까지 반영되어 있으면 조회 없이 바로 종료코드 0을 남긴다")
    void alreadyUpToDate_doesNothing() {
        given(stateRepository.findById(1)).willReturn(Optional.of(
                RecommendationHistoryState.builder().id(1).version(1L).verifiedThroughRound(10).build()));

        int exitCode = runner(10, 50).backfill();

        assertThat(exitCode).isEqualTo(0);
        verify(dhLotteryClient, Mockito.never()).fetchRound(anyInt());
    }

    /** OBS-05: 잘못된 인자로 몇 시간짜리 백필을 돌리다 뒤늦게 실패를 알아채면 안 된다. */
    @Test
    @DisplayName("OBS-05: chunk-size가 0 이하면 조회 없이 즉시 종료코드 1을 남긴다")
    void nonPositiveChunkSize_failsFastWithoutFetching() {
        RecommendationBackfillRunner runner = runner(10, 0);

        int exitCode = runner.backfill();

        assertThat(exitCode).isEqualTo(1);
        Mockito.verifyNoInteractions(dhLotteryClient);
    }

    @Test
    @DisplayName("OBS-05: request-delay-ms가 음수면 조회 없이 즉시 종료코드 1을 남긴다")
    void negativeRequestDelay_failsFastWithoutFetching() {
        RecommendationBackfillRunner runner = runner(10, 5);
        ReflectionTestUtils.setField(runner, "requestDelayMs", -1L);

        int exitCode = runner.backfill();

        assertThat(exitCode).isEqualTo(1);
        Mockito.verifyNoInteractions(dhLotteryClient);
    }

    /**
     * OBS-05: 예전에는 대기 중 인터럽트를 받아도 플래그만 다시 세우고 반복문을 계속 돌았다 —
     * 종료 신호를 받고도 다음 회차 요청을 계속 내보냈다. 지금은 인터럽트를 받으면 그 자리에서
     * 멈추고 이미 커밋된 청크까지만 반영한 채 종료코드 1을 남겨야 한다.
     */
    @Test
    @DisplayName("OBS-05: 대기 중 인터럽트를 받으면 그 자리에서 멈추고 종료코드 1을 남긴다")
    void interruptedWhileSleeping_stopsImmediately() throws InterruptedException {
        given(stateRepository.findById(1)).willReturn(Optional.empty());
        given(dhLotteryClient.fetchRound(1)).willReturn(successOf(1));
        given(importer.importHistory(any(), anyInt(), any()))
                .willReturn(new RecommendationHistoryImporter.Result(1, 0, 0));

        RecommendationBackfillRunner runner = runner(5, 1);
        // 매 회차 커밋 뒤 sleep(requestDelayMs)을 부르므로, 요청 간격을 길게 두고 그 대기를
        // 현재 스레드에 인터럽트를 걸어 끊는다.
        ReflectionTestUtils.setField(runner, "requestDelayMs", 60_000L);

        int[] exitCode = new int[1];
        Thread worker = new Thread(() -> exitCode[0] = runner.backfill());
        worker.start();
        // sleep(60000)에 실제로 진입할 시간을 준다.
        Thread.sleep(300);
        worker.interrupt();
        worker.join(5_000);

        assertThat(worker.isAlive()).as("인터럽트를 받고도 계속 돌면 안 된다").isFalse();
        assertThat(exitCode[0]).isEqualTo(1);
        // 1회차는 이미 커밋됐어야 한다(인터럽트는 그다음 대기 중에 걸렸다).
        verify(importer).importHistory(List.of(new ImportedDraw(1, List.of(1, 2, 3, 4, 5, 6))), 1,
                "dhlottery-api-backfill");
        // 3회차 이후는 인터럽트로 멈췄으니 조회조차 없어야 한다.
        Mockito.verify(dhLotteryClient, Mockito.never()).fetchRound(3);
    }

    @Test
    @DisplayName("dry-run이면 조회만 하고 커밋하지 않는다")
    void dryRun_neverCommits() {
        given(stateRepository.findById(1)).willReturn(Optional.empty());
        given(dhLotteryClient.fetchRound(1)).willReturn(successOf(1));
        given(dhLotteryClient.fetchRound(2)).willReturn(successOf(2));

        RecommendationBackfillRunner runner = runner(2, 10);
        ReflectionTestUtils.setField(runner, "dryRun", true);

        int exitCode = runner.backfill();

        assertThat(exitCode).isEqualTo(0);
        verify(importer, Mockito.never()).importHistory(any(), anyInt(), any());
    }
}
