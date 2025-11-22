package com.kraft.service;

import com.kraft.config.auth.dto.SessionUser;
import com.kraft.domain.comment.Comment;
import com.kraft.domain.comment.CommentRepository;
import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.web.dto.comment.CommentResponseDto;
import com.kraft.web.dto.comment.CommentSaveRequestDto;
import com.kraft.web.dto.comment.CommentUpdateRequestDto;
import com.kraft.common.exception.ResourceNotFoundException;
import com.kraft.common.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommentService commentService;

    @Test
    @DisplayName("댓글 작성에 성공한다")
    void save_success() {
        // given
        User author = User.of("author", "encoded", "author@example.com");
        Post post = Post.builder()
                .title("Test Post")
                .content("Test Content")
                .author(author)
                .build();

        SessionUser sessionUser = new SessionUser(author);
        CommentSaveRequestDto requestDto = CommentSaveRequestDto.builder()
                .content("Test Comment")
                .build();

        Comment comment = Comment.builder()
                .content("Test Comment")
                .post(post)
                .author(author)
                .build();

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(userRepository.findById(sessionUser.id())).willReturn(Optional.of(author));
        given(commentRepository.save(any(Comment.class))).willReturn(comment);

        // when
        Long commentId = commentService.save(1L, requestDto, sessionUser);

        // then
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    @DisplayName("댓글 수정에 성공한다")
    void update_success() {
        // given
        User author = User.builder()
                .name("author")
                .password("encoded")
                .email("author@example.com")
                .build();
        ReflectionTestUtils.setField(author, "id", 1L);

        Post post = Post.builder()
                .title("Test Post")
                .content("Test Content")
                .author(author)
                .build();

        Comment comment = Comment.builder()
                .content("Original Comment")
                .post(post)
                .author(author)
                .build();

        SessionUser sessionUser = new SessionUser(1L, "author", "author@example.com", Role.USER);
        CommentUpdateRequestDto requestDto = CommentUpdateRequestDto.builder()
                .content("Updated Comment")
                .build();

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        // when
        Long updatedId = commentService.update(1L, requestDto, sessionUser);

        // then
        assertThat(updatedId).isEqualTo(1L);
        assertThat(comment.getContent()).isEqualTo("Updated Comment");
    }

    @Test
    @DisplayName("댓글 작성자가 아니면 수정할 수 없다")
    void update_notAuthor() {
        // given
        User author = User.builder()
                .name("author")
                .password("encoded")
                .email("author@example.com")
                .build();
        ReflectionTestUtils.setField(author, "id", 1L);

        Post post = Post.builder()
                .title("Test Post")
                .content("Test Content")
                .author(author)
                .build();

        Comment comment = Comment.builder()
                .content("Original Comment")
                .post(post)
                .author(author)
                .build();

        // 다른 사용자
        SessionUser sessionUser = new SessionUser(999L, "other", "other@example.com", Role.USER);
        CommentUpdateRequestDto requestDto = CommentUpdateRequestDto.builder()
                .content("Updated Comment")
                .build();

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        // expect
        assertThatThrownBy(() -> commentService.update(1L, requestDto, sessionUser))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("댓글 작성자만");
    }

    @Test
    @DisplayName("댓글 삭제에 성공한다")
    void delete_success() {
        // given
        User author = User.builder()
                .name("author")
                .password("encoded")
                .email("author@example.com")
                .build();
        ReflectionTestUtils.setField(author, "id", 1L);

        Post post = Post.builder()
                .title("Test Post")
                .content("Test Content")
                .author(author)
                .build();

        Comment comment = Comment.builder()
                .content("Test Comment")
                .post(post)
                .author(author)
                .build();

        SessionUser sessionUser = new SessionUser(1L, "author", "author@example.com", Role.USER);

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        // when
        commentService.delete(1L, sessionUser);

        // then
        verify(commentRepository).delete(comment);
    }

    @Test
    @DisplayName("게시글의 댓글 목록을 조회할 수 있다")
    void findByPostId_success() {
        // given
        User author = User.of("author", "encoded", "author@example.com");
        Post post = Post.builder()
                .title("Test Post")
                .content("Test Content")
                .author(author)
                .build();

        Comment comment1 = Comment.builder()
                .content("Comment 1")
                .post(post)
                .author(author)
                .build();

        Comment comment2 = Comment.builder()
                .content("Comment 2")
                .post(post)
                .author(author)
                .build();

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(commentRepository.findByPostIdWithAuthor(1L))
                .willReturn(Arrays.asList(comment1, comment2));

        // when
        List<CommentResponseDto> result = commentService.findByPostId(1L);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("존재하지 않는 댓글을 수정하면 예외가 발생한다")
    void update_notFound() {
        // given
        User author = User.of("author", "encoded", "author@example.com");
        SessionUser sessionUser = new SessionUser(author);
        CommentUpdateRequestDto requestDto = CommentUpdateRequestDto.builder()
                .content("Updated Comment")
                .build();

        given(commentRepository.findById(999L)).willReturn(Optional.empty());

        // expect
        assertThatThrownBy(() -> commentService.update(999L, requestDto, sessionUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("댓글");
    }
}

