package com.kraft.service;

import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.User;
import com.kraft.web.dto.common.PageResponse;
import com.kraft.web.dto.post.PostResponseDto;
import com.kraft.web.dto.post.PostsListResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PostServicePaginationTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostService postService;

    @Test
    @DisplayName("페이지네이션으로 게시글 목록을 조회할 수 있다")
    void findAllWithPagination_success() {
        // given
        User author = User.of("author", "encoded", "author@example.com");

        Post post1 = Post.builder()
                .title("Title 1")
                .content("Content 1")
                .author(author)
                .build();

        Post post2 = Post.builder()
                .title("Title 2")
                .content("Content 2")
                .author(author)
                .build();

        Page<Post> postPage = new PageImpl<>(
                Arrays.asList(post2, post1),
                PageRequest.of(0, 10),
                2
        );

        given(postRepository.findAllWithAuthor(any(Pageable.class))).willReturn(postPage);

        // when
        PageResponse<PostsListResponseDto> result =
                postService.findAllWithPagination(0, 10, "id", "DESC");

        // then
        assertThat(result.content()).hasSize(2);
        assertThat(result.pageNumber()).isEqualTo(0);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    @DisplayName("두 번째 페이지를 조회할 수 있다")
    void findAllWithPagination_secondPage() {
        // given
        User author = User.of("author", "encoded", "author@example.com");

        Post post3 = Post.builder()
                .title("Title 3")
                .content("Content 3")
                .author(author)
                .build();

        Page<Post> postPage = new PageImpl<>(
                Arrays.asList(post3),
                PageRequest.of(1, 10),
                21 // 총 21개
        );

        given(postRepository.findAllWithAuthor(any(Pageable.class))).willReturn(postPage);

        // when
        PageResponse<PostsListResponseDto> result =
                postService.findAllWithPagination(1, 10, "id", "DESC");

        // then
        assertThat(result.pageNumber()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.first()).isFalse();
        assertThat(result.last()).isFalse();
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isTrue();
    }

    @Test
    @DisplayName("특정 사용자의 게시글 목록을 조회할 수 있다")
    void findByAuthorId_success() {
        // given
        User author = User.of("author", "encoded", "author@example.com");

        Post post1 = Post.builder()
                .title("Title 1")
                .content("Content 1")
                .author(author)
                .build();

        Post post2 = Post.builder()
                .title("Title 2")
                .content("Content 2")
                .author(author)
                .build();

        given(postRepository.findByAuthorId(1L)).willReturn(Arrays.asList(post2, post1));

        // when
        List<PostsListResponseDto> result = postService.findByAuthorId(1L);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("게시글 조회 시 조회수가 증가한다")
    void findByIdAndIncrementView_success() {
        // given
        User author = User.of("author", "encoded", "author@example.com");
        Post post = Post.builder()
                .title("Title")
                .content("Content")
                .author(author)
                .build();

        given(postRepository.findById(1L)).willReturn(java.util.Optional.of(post));

        // when
        PostResponseDto result = postService.findByIdAndIncrementView(1L);

        // then
        assertThat(result.viewCount()).isEqualTo(1L);
        assertThat(post.getViewCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("인기 게시글을 조회할 수 있다")
    void findPopularPosts_success() {
        // given
        User author = User.of("author", "encoded", "author@example.com");

        Post post1 = Post.builder()
                .title("Popular Post")
                .content("Content")
                .author(author)
                .build();

        // 조회수 증가 시뮬레이션
        post1.incrementViewCount();
        post1.incrementViewCount();
        post1.incrementViewCount();

        Post post2 = Post.builder()
                .title("Normal Post")
                .content("Content")
                .author(author)
                .build();

        Page<Post> postPage = new PageImpl<>(
                Arrays.asList(post1, post2),
                PageRequest.of(0, 10),
                2
        );

        given(postRepository.findPopularPosts(any(Pageable.class))).willReturn(postPage);

        // when
        PageResponse<PostsListResponseDto> result = postService.findPopularPosts(0, 10);

        // then
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).viewCount()).isEqualTo(3L);
    }
}
