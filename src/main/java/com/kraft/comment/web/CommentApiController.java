package com.kraft.comment.web;

import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentResponseDto;
import com.kraft.comment.dto.CommentSaveRequestDto;
import com.kraft.comment.dto.CommentUpdateRequestDto;
import com.kraft.comment.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class CommentApiController {

    private final CommentService commentService;

    @PostMapping("/api/v1/posts/{postId}/comments")
    public Long save(@PathVariable Long postId, @Valid @RequestBody CommentSaveRequestDto requestDto,
                      Authentication authentication) {
        return commentService.save(postId, authentication.getName(), requestDto);
    }

    @GetMapping("/api/v1/posts/{postId}/comments")
    public List<CommentResponseDto> findByPostId(@PathVariable Long postId) {
        return commentService.findByPostId(postId);
    }

    /**
     * 상세 화면 "더 보기"가 쓰는 커서 페이지. {@code canManage}가 화면 전용 필드라 위의 공개
     * REST({@code findByPostId})와 분리하고, 인증 정보로 요청자별 권한을 판정한다(개선 보고서
     * "댓글 전체 로딩").
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
