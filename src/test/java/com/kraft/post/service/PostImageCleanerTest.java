package com.kraft.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * 주기 진입점({@code clean()})의 스위치와 배치 반복(id 커서 진행, 상한)만 본다. 배치 하나 안의
 * 실제 조회·삭제·실패 처리는 {@link PostImageCleanupBatchRunnerTest}가 본다 — B06으로 그 부분이
 * 별도 빈({@link PostImageCleanupBatchRunner})으로 분리되면서 이 클래스는 더 이상 리포지토리·
 * 파일 서비스를 직접 알지 못한다.
 */
@ExtendWith(MockitoExtension.class)
class PostImageCleanerTest {

    @Mock
    private PostImageCleanupBatchRunner batchRunner;

    @InjectMocks
    private PostImageCleaner postImageCleaner;

    @BeforeEach
    void enableCleanup() {
        ReflectionTestUtils.setField(postImageCleaner, "enabled", true);
    }

    @Test
    @DisplayName("정리 스위치를 끄면 주기 실행이 배치 실행기를 한 번도 부르지 않는다")
    void clean_whenDisabled_doesNothing() {
        ReflectionTestUtils.setField(postImageCleaner, "enabled", false);

        postImageCleaner.clean();

        then(batchRunner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("주기 실행은 삭제 예약분과 만료된 미연결 업로드를 모두 치운다")
    void clean_removesPendingDeletionsAndExpiredOrphans() {
        given(batchRunner.cleanPendingDeletionsBatch(anyLong()))
                .willReturn(new PostImageCleanupBatchRunner.BatchResult(1, 1L, 1))
                .willReturn(PostImageCleanupBatchRunner.BatchResult.empty(1L));
        given(batchRunner.cleanExpiredOrphansBatch(anyLong(), any(LocalDateTime.class)))
                .willReturn(new PostImageCleanupBatchRunner.BatchResult(1, 1L, 1))
                .willReturn(PostImageCleanupBatchRunner.BatchResult.empty(1L));

        postImageCleaner.clean();

        then(batchRunner).should(org.mockito.Mockito.atLeastOnce()).cleanPendingDeletionsBatch(anyLong());
        then(batchRunner).should(org.mockito.Mockito.atLeastOnce()).cleanExpiredOrphansBatch(anyLong(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("B06: 배치가 한 페이지를 꽉 채우면 마지막 id를 이어서 다음 배치를 부르고, 덜 채우면 멈춘다")
    void cleanPendingDeletions_whenBatchFillsAPage_continuesFromLastId() {
        given(batchRunner.cleanPendingDeletionsBatch(0L))
                .willReturn(new PostImageCleanupBatchRunner.BatchResult(
                        PostImageCleaner.CLEANUP_BATCH_SIZE, 200L, PostImageCleaner.CLEANUP_BATCH_SIZE));
        given(batchRunner.cleanPendingDeletionsBatch(200L))
                .willReturn(new PostImageCleanupBatchRunner.BatchResult(1, 201L, 1));

        int deleted = postImageCleaner.cleanPendingDeletions();

        assertThat(deleted).isEqualTo(PostImageCleaner.CLEANUP_BATCH_SIZE + 1);
        then(batchRunner).should().cleanPendingDeletionsBatch(0L);
        then(batchRunner).should().cleanPendingDeletionsBatch(200L);
    }

    @Test
    @DisplayName("치울 것이 없으면 배치를 한 번만 불러보고 끝낸다")
    void cleanPendingDeletions_whenNothingReserved_stopsAfterFirstEmptyBatch() {
        given(batchRunner.cleanPendingDeletionsBatch(0L))
                .willReturn(PostImageCleanupBatchRunner.BatchResult.empty(0L));

        assertThat(postImageCleaner.cleanPendingDeletions()).isZero();
        then(batchRunner).should().cleanPendingDeletionsBatch(0L);
    }

    @Test
    @DisplayName("id를 지정하면 배치 실행기의 범위 조회 API를 부르지 않고 지정 대상만 위임한다")
    void cleanPendingDeletionsFor_delegatesToBatchRunner() {
        given(batchRunner.cleanPendingDeletionsFor(List.of(7L))).willReturn(1);

        int deleted = postImageCleaner.cleanPendingDeletionsFor(List.of(7L));

        assertThat(deleted).isEqualTo(1);
        then(batchRunner).should(org.mockito.Mockito.never()).cleanPendingDeletionsBatch(anyLong());
    }
}
