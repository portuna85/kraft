package com.kraft.comment.web;

import com.kraft.comment.dto.CommentDeleteResultDto;
import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentSaveRequestDto;
import com.kraft.comment.dto.CommentUpdateRequestDto;
import com.kraft.comment.dto.CommentViewDto;
import com.kraft.comment.service.CommentService;
import com.kraft.shared.web.EntityTags;
import com.kraft.shared.web.RateLimitResponses;
import com.kraft.shared.web.WriteRateLimiters;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class CommentApiController {

    private final CommentService commentService;
    private final WriteRateLimiters rateLimiters;

    /** 작성은 속도를 제한한다({@code WriteRateLimiters}). */
    @PostMapping("/api/v1/posts/{postId}/comments")
    public ResponseEntity<?> save(@PathVariable Long postId, @Valid @RequestBody CommentSaveRequestDto requestDto,
                                   Authentication authentication) {
        if (!rateLimiters.tryAcquireComment(authentication)) {
            return RateLimitResponses.tooManyRequests("COMMENT_RATE_LIMITED", 60);
        }
        return ResponseEntity.ok(commentService.save(postId, authentication, requestDto));
    }

    /** 상세 화면 "더 보기"의 커서 페이지. {@code canManage}는 화면 전용 필드라 인증 정보로 요청자별 권한을 판정한다. */
    @GetMapping("/api/v1/posts/{postId}/comments/page")
    public CommentPageDto page(@PathVariable Long postId,
                                @RequestParam(required = false) Long afterId,
                                Authentication authentication) {
        return commentService.findNextPageForView(postId, afterId, authentication);
    }

    /** "답글 더 보기" — 개수 상한에 걸려도 남은 답글에 항상 닿아야 한다. 게시글 목록 GET처럼 익명도 볼 수 있다(SecurityConfig). */
    @GetMapping("/api/v1/comments/{parentId}/replies")
    public CommentPageDto replies(@PathVariable Long parentId,
                                   @RequestParam(required = false) Long afterId,
                                   Authentication authentication) {
        return commentService.findRepliesPage(parentId, afterId, authentication);
    }

    /** 글 수정과 같은 계약: 기준 버전은 {@code If-Match}(각 댓글의 {@code version})로 보내며 다르면 412, 없으면 428, 응답 ETag는 저장 뒤의 새 버전이다. */
    @PutMapping("/api/v1/comments/{id}")
    public ResponseEntity<CommentViewDto> update(@PathVariable Long id,
                                                  @Valid @RequestBody CommentUpdateRequestDto requestDto,
                                                  @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                  Authentication authentication) {
        CommentViewDto saved = commentService.update(
                id, requestDto, EntityTags.expectedVersion(ifMatch), authentication);
        return ResponseEntity.ok().eTag(EntityTags.of(saved.version())).body(saved);
    }

    @DeleteMapping("/api/v1/comments/{id}")
    public CommentDeleteResultDto delete(@PathVariable Long id, Authentication authentication) {
        return commentService.delete(id, authentication);
    }
}
