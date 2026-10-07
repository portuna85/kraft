package com.kraft.comment.web;

import com.kraft.comment.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 댓글 API. 경로가 {@code /api/v1/admin/**}라 {@code SecurityConfig}가 관리자만 들여보낸다 —
 * 이 컨트롤러는 권한을 다시 판정하지 않는다.
 */
@RequiredArgsConstructor
@RestController
public class AdminCommentApiController {

    private final CommentService commentService;

    /** 신고 처리로 숨겨진 댓글의 숨김을 푼다. */
    @PostMapping("/api/v1/admin/comments/{id}/unblind")
    public ResponseEntity<Void> unblind(@PathVariable Long id) {
        commentService.unblind(id);
        return ResponseEntity.noContent().build();
    }
}
