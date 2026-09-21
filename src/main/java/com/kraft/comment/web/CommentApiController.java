package com.kraft.comment.web;

import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentSaveRequestDto;
import com.kraft.comment.dto.CommentUpdateRequestDto;
import com.kraft.comment.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class CommentApiController {

    private final CommentService commentService;

    @PostMapping("/api/v1/posts/{postId}/comments")
    public Long save(@PathVariable Long postId, @Valid @RequestBody CommentSaveRequestDto requestDto,
                      Authentication authentication) {
        return commentService.save(postId, authentication.getName(), requestDto);
    }

    /**
     * 상세 화면 "더 보기"가 쓰는 커서 페이지. {@code canManage}는 화면 전용 필드라 인증 정보로
     * 요청자별 권한을 판정한다(개선 보고서 "댓글 전체 로딩").
     * <p>
     * 예전에는 익명 GET으로 게시글의 댓글·답글 전체를 한 번에 반환하는 별도 API
     * ({@code GET /api/v1/posts/{postId}/comments}, {@code findByPostId})가 있었다(B08) —
     * 이 커서 API와 별개로 존재했고, 프런트엔드 어디서도 호출하지 않았다. 답글이 많이 달린
     * 공개 게시글을 대상으로 익명 요청 한 번에 무제한 양을 응답할 수 있어 폐기했다.
     */
    @GetMapping("/api/v1/posts/{postId}/comments/page")
    public CommentPageDto page(@PathVariable Long postId,
                                @RequestParam(required = false) Long afterId,
                                Authentication authentication) {
        return commentService.findNextPageForView(postId, afterId, authentication);
    }

    @PutMapping("/api/v1/comments/{id}")
    public Long update(@PathVariable Long id, @Valid @RequestBody CommentUpdateRequestDto requestDto,
                        Authentication authentication) {
        return commentService.update(id, requestDto, authentication);
    }

    @DeleteMapping("/api/v1/comments/{id}")
    public Long delete(@PathVariable Long id, Authentication authentication) {
        commentService.delete(id, authentication);
        return id;
    }
}
