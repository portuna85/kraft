package com.kraft.web.api;

import com.kraft.config.auth.LoginUser;
import com.kraft.config.auth.dto.SessionUser;
import com.kraft.service.CommentService;
import com.kraft.web.dto.comment.CommentResponseDto;
import com.kraft.web.dto.comment.CommentSaveRequestDto;
import com.kraft.web.dto.comment.CommentUpdateRequestDto;
import com.kraft.web.dto.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 댓글 API 컨트롤러
 * /api/v1/posts/{postId}/comments
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
public class CommentApiController {

    private final CommentService commentService;

    /**
     * 댓글 작성
     * POST /api/v1/posts/{postId}/comments
     */
    @PostMapping
    public ResponseEntity<Long> createComment(
            @PathVariable Long postId,
            @RequestBody @Valid CommentSaveRequestDto requestDto,
            @LoginUser SessionUser sessionUser
    ) {
        Long commentId = commentService.save(postId, requestDto, sessionUser);
        log.info("댓글 작성 API 호출: postId={}, commentId={}, authorId={}",
                postId, commentId, sessionUser.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(commentId);
    }

    /**
     * 답글 작성 (대댓글)
     * POST /api/v1/posts/{postId}/comments/{parentId}/replies
     */
    @PostMapping("/{parentId}/replies")
    public ResponseEntity<Long> createReply(
            @PathVariable Long postId,
            @PathVariable Long parentId,
            @RequestBody @Valid CommentSaveRequestDto requestDto,
            @LoginUser SessionUser sessionUser
    ) {
        Long replyId = commentService.saveReply(postId, parentId, requestDto, sessionUser);
        log.info("답글 작성 API 호출: postId={}, parentId={}, replyId={}",
                postId, parentId, replyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(replyId);
    }

    /**
     * 댓글 수정
     * PUT /api/v1/posts/{postId}/comments/{commentId}
     */
    @PutMapping("/{commentId}")
    public ResponseEntity<Long> updateComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestBody @Valid CommentUpdateRequestDto requestDto,
            @LoginUser SessionUser sessionUser
    ) {
        Long updatedId = commentService.update(commentId, requestDto, sessionUser);
        log.info("댓글 수정 API 호출: commentId={}", updatedId);
        return ResponseEntity.ok(updatedId);
    }

    /**
     * 댓글 삭제
     * DELETE /api/v1/posts/{postId}/comments/{commentId}
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @LoginUser SessionUser sessionUser
    ) {
        commentService.delete(commentId, sessionUser);
        log.info("댓글 삭제 API 호출: commentId={}", commentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 특정 게시글의 댓글 목록 조회
     * GET /api/v1/posts/{postId}/comments
     */
    @GetMapping
    public ResponseEntity<List<CommentResponseDto>> getComments(@PathVariable Long postId) {
        List<CommentResponseDto> comments = commentService.findByPostId(postId);
        return ResponseEntity.ok(comments);
    }

    /**
     * 특정 게시글의 부모 댓글만 조회 (대댓글 제외)
     * GET /api/v1/posts/{postId}/comments/parents
     */
    @GetMapping("/parents")
    public ResponseEntity<List<CommentResponseDto>> getParentComments(@PathVariable Long postId) {
        List<CommentResponseDto> comments = commentService.findParentCommentsByPostId(postId);
        return ResponseEntity.ok(comments);
    }

    /**
     * 특정 게시글의 부모 댓글 페이징 조회
     * GET /api/v1/posts/{postId}/comments/page?page=0&size=10
     */
    @GetMapping("/page")
    public ResponseEntity<PageResponse<CommentResponseDto>> getParentCommentsWithPagination(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CommentResponseDto> response =
                commentService.findParentCommentsWithPagination(postId, page, size);
        log.info("댓글 페이징 조회 API 호출: postId={}, page={}, results={}",
                postId, page, response.totalElements());
        return ResponseEntity.ok(response);
    }

    /**
     * 특정 댓글의 답글 목록 조회
     * GET /api/v1/posts/{postId}/comments/{parentId}/replies
     */
    @GetMapping("/{parentId}/replies")
    public ResponseEntity<List<CommentResponseDto>> getReplies(
            @PathVariable Long postId,
            @PathVariable Long parentId
    ) {
        List<CommentResponseDto> replies = commentService.findRepliesByParentId(parentId);
        return ResponseEntity.ok(replies);
    }

    /**
     * 특정 게시글의 댓글 수 조회
     * GET /api/v1/posts/{postId}/comments/count
     */
    @GetMapping("/count")
    public ResponseEntity<Long> getCommentCount(@PathVariable Long postId) {
        long count = commentService.countByPostId(postId);
        return ResponseEntity.ok(count);
    }
}
