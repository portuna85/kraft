package com.kraft.service;

import com.kraft.config.auth.dto.SessionUser;
import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.web.dto.post.PostResponseDto;
import com.kraft.web.dto.post.PostSaveRequestDto;
import com.kraft.web.dto.post.PostUpdateRequestDto;
import com.kraft.web.dto.post.PostsListResponseDto;
import com.kraft.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostService postService;

    @Test
    @DisplayName("게시글 작성에 성공한다")
    void save_success() {
        // given
        User author = User.of("author", "encoded", "author@example.com");
        SessionUser sessionUser = new SessionUser(author);

        PostSaveRequestDto requestDto = PostSaveRequestDto.builder()
                .title("Test Title")
                .content("Test Content")
                .build();

        given(userRepository.findById(sessionUser.id())).willReturn(Optional.of(author));
        given(postRepository.save(any(Post.class))).willAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            return Post.builder()
                    .title(post.getTitle())
                    .content(post.getContent())
                    .author(post.getAuthor())
                    .build();
        });

        // when
        Long postId = postService.save(requestDto, sessionUser);

        // then
        verify(userRepository).findById(sessionUser.id());
        verify(postRepository).save(any(Post.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 게시글 작성하면 예외가 발생한다")
    void save_userNotFound() {
        // given
        User author = User.of("author", "encoded", "author@example.com");
        SessionUser sessionUser = new SessionUser(author);

        PostSaveRequestDto requestDto = PostSaveRequestDto.builder()
                .title("Test Title")
                .content("Test Content")
                .build();

        given(userRepository.findById(sessionUser.id())).willReturn(Optional.empty());

        // expect
        assertThatThrownBy(() -> postService.save(requestDto, sessionUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("사용자");
    }

    @Test
    @DisplayName("게시글 수정에 성공한다")
    void update_success() {
        // given
        User author = User.of("author", "encoded", "author@example.com");
        Post post = Post.builder()
                .title("Original Title")
                .content("Original Content")
                .author(author)
                .build();

        PostUpdateRequestDto requestDto = PostUpdateRequestDto.builder()
                .title("Updated Title")
                .content("Updated Content")
                .build();

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        // when
        Long result = postService.update(1L, requestDto);

        // then
        assertThat(result).isEqualTo(1L);
        assertThat(post.getTitle()).isEqualTo("Updated Title");
        assertThat(post.getContent()).isEqualTo("Updated Content");
    }

    @Test
    @DisplayName("게시글 삭제에 성공한다")
    void delete_success() {
        // given
        User author = User.of("author", "encoded", "author@example.com");
        Post post = Post.builder()
                .title("Title")
                .content("Content")
                .author(author)
                .build();

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        // when
        postService.delete(1L);

        // then
        verify(postRepository).delete(post);
    }

    @Test
    @DisplayName("게시글 단건 조회에 성공한다")
    void findById_success() {
        // given
        User author = User.of("author", "encoded", "author@example.com");
        Post post = Post.builder()
                .title("Title")
                .content("Content")
                .author(author)
                .build();

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        // when
        PostResponseDto result = postService.findById(1L);

        // then
        assertThat(result.title()).isEqualTo("Title");
        assertThat(result.content()).isEqualTo("Content");
        assertThat(result.author()).isEqualTo("author");
    }

    @Test
    @DisplayName("게시글 목록 조회에 성공한다")
    void findAllDesc_success() {
        // given
        User author = User.of("author", "encoded", "author@example.com");
        Post post1 = Post.builder().title("Title 1").content("Content 1").author(author).build();
        Post post2 = Post.builder().title("Title 2").content("Content 2").author(author).build();

        given(postRepository.findAllDesc()).willReturn(Arrays.asList(post2, post1));

        // when
        List<PostsListResponseDto> result = postService.findAllDesc();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Title 2");
        assertThat(result.get(1).title()).isEqualTo("Title 1");
    }

    @Test
    @DisplayName("존재하지 않는 게시글 조회하면 예외가 발생한다")
    void findById_notFound() {
        // given
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        // expect
        assertThatThrownBy(() -> postService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("게시글");
    }
}

