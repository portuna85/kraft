package com.kraft.post.service;

import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PostModerationServiceTest {

    @Mock
    private PostRepository postRepository;

    private PostModerationService service;

    @BeforeEach
    void setUp() {
        service = new PostModerationService(postRepository);
    }

    private static Post post() {
        return Post.builder().title("제목").content("내용").build();
    }

    @Test
    @DisplayName("unblindPost: 숨겨진 글이 아니면 글이 없는 것으로 답한다")
    void unblindPost_whenNotBlinded_throwsNotFound() {
        given(postRepository.unblind(1L)).willReturn(0);

        assertThatThrownBy(() -> service.unblindPost(1L)).isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("restore: 삭제된 글이 아니면 글이 없는 것으로 답한다")
    void restore_whenNotDeleted_throwsNotFound() {
        given(postRepository.restore(1L)).willReturn(0);

        assertThatThrownBy(() -> service.restore(1L)).isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("pin: 기한과 개수를 통과하면 고정한다")
    void pin_whenWithinLimits_pins() {
        LocalDateTime until = LocalDateTime.now().plusDays(7);
        given(postRepository.countPinnedExcluding(eq(1L), any())).willReturn(4L);
        given(postRepository.pin(1L, until)).willReturn(1);

        assertThatCode(() -> service.pin(1L, until)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("pin: 기한이 365일을 넘으면 거절하고 아무것도 바꾸지 않는다")
    void pin_whenDeadlineTooFar_isRejected() {
        LocalDateTime tooFar = LocalDateTime.now().plusDays(PostModerationService.MAX_PIN_DAYS + 1);

        assertThatThrownBy(() -> service.pin(1L, tooFar))
                .isInstanceOf(com.kraft.shared.exception.BusinessValidationException.class)
                .hasMessageContaining("365일");

        org.mockito.Mockito.verify(postRepository, org.mockito.Mockito.never()).pin(any(), any());
    }

    @Test
    @DisplayName("pin: 이미 5개가 고정 중이면 새로 고정하지 못한다(자기 기한을 바꾸는 경우는 센 개수에서 빠진다)")
    void pin_whenAlreadyAtLimit_isRejected() {
        given(postRepository.countPinnedExcluding(eq(1L), any())).willReturn((long) PostQueryService.PINNED_LIMIT);

        assertThatThrownBy(() -> service.pin(1L, LocalDateTime.now().plusDays(1)))
                .isInstanceOf(com.kraft.shared.exception.BusinessValidationException.class)
                .hasMessageContaining("최대 5개");

        org.mockito.Mockito.verify(postRepository, org.mockito.Mockito.never()).pin(any(), any());
    }

    @Test
    @DisplayName("pin·unpin: 보이지 않는 글이거나 고정된 글이 아니면 글이 없는 것으로 답한다")
    void pinAndUnpin_whenNotApplicable_throwNotFound() {
        LocalDateTime until = LocalDateTime.now().plusDays(1);
        given(postRepository.countPinnedExcluding(eq(1L), any())).willReturn(0L);
        given(postRepository.pin(1L, until)).willReturn(0);
        given(postRepository.unpin(2L)).willReturn(0);

        assertThatThrownBy(() -> service.pin(1L, until)).isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> service.unpin(2L)).isInstanceOf(PostNotFoundException.class);
    }
}
