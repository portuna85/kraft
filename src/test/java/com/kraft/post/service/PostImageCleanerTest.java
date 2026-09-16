package com.kraft.post.service;

import com.kraft.post.domain.PostImage;
import com.kraft.post.domain.PostImageRepository;
import com.kraft.post.domain.PostImageStatus;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * 정리 작업의 주기 진입점과 실패 처리. "무엇이 지워지는가"는 실제 파일·DB로 도는
 * {@link PostImageLifecycleTest}가 보고, 여기서는 그 테스트가 다루지 않는 두 가지를 본다:
 * 스위치를 끈 상태의 주기 실행과, 파일 하나가 안 지워질 때의 동작이다.
 * <p>
 * 후자는 실제로 일어난다 — 다른 프로세스가 파일을 열고 있거나 권한이 없으면
 * {@code deleteIfExists}가 던진다. 그때 그 행이 지워져 버리면 파일은 디스크에 영원히 남고
 * 아무도 다시 시도하지 않는다. 그래서 "행을 남긴다"가 이 클래스의 계약이다.
 */
@ExtendWith(MockitoExtension.class)
class PostImageCleanerTest {

    @Mock
    private PostImageRepository postImageRepository;

    @Mock
    private PostImageService postImageService;

    @InjectMocks
    private PostImageCleaner postImageCleaner;

    @BeforeEach
    void enableCleanup() {
        ReflectionTestUtils.setField(postImageCleaner, "enabled", true);
    }

    private static PostImage image(String fileName) {
        PostImage image = PostImage.builder().fileName(fileName).owner(null).sizeBytes(1L).build();
        ReflectionTestUtils.setField(image, "id", 1L);
        return image;
    }

    @Test
    @DisplayName("정리 스위치를 끄면 주기 실행이 아무것도 조회하지 않고 돌아간다")
    void clean_whenDisabled_doesNothing() {
        ReflectionTestUtils.setField(postImageCleaner, "enabled", false);

        postImageCleaner.clean();

        then(postImageRepository).shouldHaveNoInteractions();
        then(postImageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("주기 실행은 삭제 예약분과 만료된 미연결 업로드를 모두 치운다")
    void clean_removesPendingDeletionsAndExpiredOrphans() {
        given(postImageRepository.findAllByStatus(PostImageStatus.PENDING_DELETE))
                .willReturn(List.of(image("pending.png")));
        given(postImageRepository.findAllByStatusAndCreatedAtBefore(eq(PostImageStatus.ORPHAN), any(LocalDateTime.class)))
                .willReturn(List.of(image("orphan.png")));

        postImageCleaner.clean();

        then(postImageService).should().deleteIfExists("/images/pending.png");
        then(postImageService).should().deleteIfExists("/images/orphan.png");
        then(postImageRepository).should(org.mockito.Mockito.times(2)).delete(any(PostImage.class));
    }

    @Test
    @DisplayName("한 파일이 지워지지 않아도 나머지는 계속 치우고, 실패한 행은 남겨 다음 주기에 다시 시도한다")
    void cleanPendingDeletions_whenOneFileFails_keepsItsRowAndContinues() {
        PostImage failing = image("locked.png");
        PostImage succeeding = image("fine.png");
        given(postImageRepository.findAllByStatus(PostImageStatus.PENDING_DELETE))
                .willReturn(List.of(failing, succeeding));
        willThrow(new IllegalArgumentException("이미지 삭제에 실패했습니다."))
                .given(postImageService).deleteIfExists("/images/locked.png");
        willDoNothing().given(postImageService).deleteIfExists("/images/fine.png");

        int deleted = postImageCleaner.cleanPendingDeletions();

        assertThat(deleted).isEqualTo(1);
        then(postImageRepository).should().delete(succeeding);
        // 행이 남아 있어야 다음 주기가 이 파일을 다시 집는다.
        then(postImageRepository).should(never()).delete(failing);
    }

    @Test
    @DisplayName("치울 것이 없으면 파일 삭제를 한 번도 시도하지 않는다")
    void cleanPendingDeletions_whenNothingReserved_touchesNoFile() {
        given(postImageRepository.findAllByStatus(PostImageStatus.PENDING_DELETE)).willReturn(List.of());

        assertThat(postImageCleaner.cleanPendingDeletions()).isZero();
        then(postImageService).should(never()).deleteIfExists(anyString());
    }

    @Test
    @DisplayName("id를 지정하면 그 이미지만 조회·삭제하고 전체 대기열은 건드리지 않는다")
    void cleanPendingDeletionsFor_touchesOnlySpecifiedIds() {
        given(postImageRepository.findAllByIdInAndStatus(List.of(7L), PostImageStatus.PENDING_DELETE))
                .willReturn(List.of(image("scoped.png")));

        int deleted = postImageCleaner.cleanPendingDeletionsFor(List.of(7L));

        assertThat(deleted).isEqualTo(1);
        then(postImageService).should().deleteIfExists("/images/scoped.png");
        then(postImageRepository).should(never()).findAllByStatus(any());
    }

    @Test
    @DisplayName("id 목록이 비어 있으면 아무것도 조회하지 않는다")
    void cleanPendingDeletionsFor_whenIdsEmpty_touchesNothing() {
        int deleted = postImageCleaner.cleanPendingDeletionsFor(List.of());

        assertThat(deleted).isZero();
        then(postImageRepository).shouldHaveNoInteractions();
        then(postImageService).shouldHaveNoInteractions();
    }
}
