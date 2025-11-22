package com.kraft.web.api;

import com.kraft.config.auth.LoginUser;
import com.kraft.config.auth.dto.SessionUser;
import com.kraft.service.PostService;
import com.kraft.web.dto.common.PageResponse;
import com.kraft.web.dto.post.PostResponseDto;
import com.kraft.web.dto.post.PostSaveRequestDto;
import com.kraft.web.dto.post.PostUpdateRequestDto;
import com.kraft.web.dto.post.PostsListResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/posts")
public class PostApiController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<Long> createPost(
            @RequestBody @Valid PostSaveRequestDto requestDto,
            @LoginUser SessionUser sessionUser
    ) {
        Long postId = postService.save(requestDto, sessionUser);
        log.info("게시글 작성 API 호출 성공: postId={}, authorId={}", postId, sessionUser.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(postId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Long> updatePost(
            @PathVariable Long id,
            @RequestBody @Valid PostUpdateRequestDto requestDto
    ) {
        Long updatedId = postService.update(id, requestDto);
        log.info("게시글 수정 API 호출 성공: postId={}", updatedId);
        return ResponseEntity.ok(updatedId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        postService.delete(id);
        log.info("게시글 삭제 API 호출 성공: postId={}", id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponseDto> getPost(@PathVariable Long id) {
        PostResponseDto post = postService.findByIdAndIncrementView(id);
        return ResponseEntity.ok(post);
    }

    @GetMapping("/list")
    public ResponseEntity<List<PostsListResponseDto>> getPostList() {
        List<PostsListResponseDto> posts = postService.findAllDesc();
        return ResponseEntity.ok(posts);
    }

    /**
     * 페이지네이션 게시글 목록 조회
     * GET /api/v1/posts?page=0&size=10&sort=id&direction=DESC
     */
    @GetMapping
    public ResponseEntity<PageResponse<PostsListResponseDto>> getPostsWithPagination(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "DESC") String direction
    ) {
        PageResponse<PostsListResponseDto> response =
                postService.findAllWithPagination(page, size, sort, direction);
        return ResponseEntity.ok(response);
    }

    /**
     * 특정 사용자의 게시글 목록 조회
     * GET /api/v1/posts/author/{authorId}
     */
    @GetMapping("/author/{authorId}")
    public ResponseEntity<List<PostsListResponseDto>> getPostsByAuthor(
            @PathVariable Long authorId
    ) {
        List<PostsListResponseDto> posts = postService.findByAuthorId(authorId);
        return ResponseEntity.ok(posts);
    }

    /**
     * 게시글 검색
     * GET /api/v1/posts/search?keyword=검색어&page=0&size=10
     */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<PostsListResponseDto>> searchPosts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PostsListResponseDto> response =
                postService.searchPosts(keyword, page, size);
        log.info("게시글 검색 API 호출: keyword={}, results={}", keyword, response.totalElements());
        return ResponseEntity.ok(response);
    }

    /**
     * 인기 게시글 조회 (조회수 기준)
     * GET /api/v1/posts/popular?page=0&size=10
     */
    @GetMapping("/popular")
    public ResponseEntity<PageResponse<PostsListResponseDto>> getPopularPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PostsListResponseDto> response = postService.findPopularPosts(page, size);
        log.info("인기 게시글 API 호출: page={}, results={}", page, response.totalElements());
        return ResponseEntity.ok(response);
    }
}
