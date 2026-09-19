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
