package com.kraft.post.service;

import com.kraft.post.domain.PostImage;
import com.kraft.post.domain.PostImageRepository;
import com.kraft.post.domain.PostImageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * 배치 하나(최대 {@code PostImageCleaner.CLEANUP_BATCH_SIZE}행) 안의 조회·삭제·실패 처리를
 * 본다. 여러 배치를 몇 번 반복하는지는 {@link PostImageCleaner}(id 커서 진행, 상한)의 책임이고
 * 여기서는 다루지 않는다 — "무엇이 지워지는가"는 실제 파일·DB로 도는 {@link PostImageLifecycleTest}가
 * 본다.
 */
@ExtendWith(MockitoExtension.class)
class PostImageCleanupBatchRunnerTest {

    @Mock
    private PostImageRepository postImageRepository;

    @Mock
    private PostImageService postImageService;

    @InjectMocks
    private PostImageCleanupBatchRunner batchRunner;

    private static PostImage image(String fileName, long id) {
        PostImage image = PostImage.builder().fileName(fileName).owner(null).sizeBytes(1L).build();
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }

    @Test
    @DisplayName("B01: 파일을 지우기 전 조건부 선점이 0행이면(그 사이 연결됨) 파일을 건드리지 않는다")
    void cleanExpiredOrphansBatch_whenClaimFails_skipsFile() {
        given(postImageRepository.findAllByStatusAndCreatedAtBeforeAndIdGreaterThanOrderByIdAsc(
                eq(PostImageStatus.ORPHAN), any(LocalDateTime.class), eq(0L), any(Pageable.class)))
                .willReturn(List.of(image("attached-in-between.png", 1L)));
        given(postImageRepository.claimExpiredOrphanForDeletion(eq(1L), any(LocalDateTime.class))).willReturn(0);

        PostImageCleanupBatchRunner.BatchResult result =
                batchRunner.cleanExpiredOrphansBatch(0L, LocalDateTime.now());

        assertThat(result.deleted()).isZero();
        assertThat(result.pageSize()).isEqualTo(1);
        assertThat(result.lastId()).isEqualTo(1L);
        then(postImageService).should(never()).deleteIfExists(anyString());
        then(postImageRepository).should(never()).delete(any(PostImage.class));
    }

    @Test
    @DisplayName("한 파일이 지워지지 않아도 나머지는 계속 치우고, 실패한 행은 남겨 다음 주기에 다시 시도한다")
    void cleanPendingDeletionsBatch_whenOneFileFails_keepsItsRowAndContinues() {
        PostImage failing = image("locked.png", 1L);
        PostImage succeeding = image("fine.png", 2L);
        given(postImageRepository.findAllByStatusAndIdGreaterThanOrderByIdAsc(
                eq(PostImageStatus.PENDING_DELETE), eq(0L), any(Pageable.class)))
                .willReturn(List.of(failing, succeeding));
        willThrow(new IllegalArgumentException("이미지 삭제에 실패했습니다."))
                .given(postImageService).deleteIfExists("/images/locked.png");
        willDoNothing().given(postImageService).deleteIfExists("/images/fine.png");

        PostImageCleanupBatchRunner.BatchResult result = batchRunner.cleanPendingDeletionsBatch(0L);

        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(2);
        assertThat(result.lastId()).isEqualTo(2L);
        then(postImageRepository).should().delete(succeeding);
        // 행이 남아 있어야 다음 배치가 이 파일을 다시 집는다.
        then(postImageRepository).should(never()).delete(failing);
    }

    @Test
    @DisplayName("치울 것이 없으면 파일 삭제를 한 번도 시도하지 않는다")
    void cleanPendingDeletionsBatch_whenNothingReserved_touchesNoFile() {
        given(postImageRepository.findAllByStatusAndIdGreaterThanOrderByIdAsc(
                eq(PostImageStatus.PENDING_DELETE), eq(0L), any(Pageable.class)))
                .willReturn(List.of());

        PostImageCleanupBatchRunner.BatchResult result = batchRunner.cleanPendingDeletionsBatch(0L);

        assertThat(result.deleted()).isZero();
        assertThat(result.pageSize()).isZero();
        then(postImageService).should(never()).deleteIfExists(anyString());
    }

    @Test
    @DisplayName("id를 지정하면 그 이미지만 조회·삭제하고 범위 조회 API는 부르지 않는다")
    void cleanPendingDeletionsFor_touchesOnlySpecifiedIds() {
        given(postImageRepository.findAllByIdInAndStatus(List.of(7L), PostImageStatus.PENDING_DELETE))
                .willReturn(List.of(image("scoped.png", 7L)));

        int deleted = batchRunner.cleanPendingDeletionsFor(List.of(7L));

        assertThat(deleted).isEqualTo(1);
        then(postImageService).should().deleteIfExists("/images/scoped.png");
        then(postImageRepository).should(never())
                .findAllByStatusAndIdGreaterThanOrderByIdAsc(any(), any(), any());
    }

    @Test
    @DisplayName("id 목록이 비어 있으면 아무것도 조회하지 않는다")
    void cleanPendingDeletionsFor_whenIdsEmpty_touchesNothing() {
        int deleted = batchRunner.cleanPendingDeletionsFor(List.of());

        assertThat(deleted).isZero();
        then(postImageRepository).shouldHaveNoInteractions();
        then(postImageService).shouldHaveNoInteractions();
    }
}
