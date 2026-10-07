package com.kraft.post.web;

import com.kraft.post.service.PostModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 게시글 API. 경로가 {@code /api/v1/admin/**}라 {@code SecurityConfig}가 관리자만 들여보낸다 —
 * 이 컨트롤러는 권한을 다시 판정하지 않는다.
 */
@RequiredArgsConstructor
@RestController
public class AdminPostApiController {

    private final PostModerationService postModerationService;

    /** 소프트 삭제된 글을 복구한다. */
    @PostMapping("/api/v1/admin/posts/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable Long id) {
        postModerationService.restore(id);
        return ResponseEntity.noContent().build();
    }
}
