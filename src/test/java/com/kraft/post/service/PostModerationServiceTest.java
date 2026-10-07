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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

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
    @DisplayName("blindPost: 숨기면 끝이고, 이미 숨겨진 글은 조용히 넘어간다(신고가 여럿이거나 다른 관리자가 먼저 처리)")
    void blindPost_isIdempotentForAlreadyBlindedPost() {
        Post blinded = post();
        ReflectionTestUtils.setField(blinded, "blindedAt", LocalDateTime.now());
        given(postRepository.blind(eq(1L), any())).willReturn(0);
        given(postRepository.findById(1L)).willReturn(Optional.of(blinded));

        assertThatCode(() -> service.blindPost(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("blindPost: 없거나 삭제된 글이면 글이 없는 것으로 답한다")
    void blindPost_whenMissingOrDeleted_throwsNotFound() {
        Post deleted = post();
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        given(postRepository.blind(eq(1L), any())).willReturn(0);
        given(postRepository.blind(eq(2L), any())).willReturn(0);
        given(postRepository.findById(1L)).willReturn(Optional.empty());
        given(postRepository.findById(2L)).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.blindPost(1L)).isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> service.blindPost(2L)).isInstanceOf(PostNotFoundException.class);
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
}
