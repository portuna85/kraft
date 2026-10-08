package com.kraft.post.web;

import com.kraft.post.dto.PostPinRequestDto;
import com.kraft.post.service.PostModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 글을 숨긴다. 내용은 그대로 남고 관리자만 볼 수 있으며 숨김 해제로 되돌린다. */
    @PostMapping("/api/v1/admin/posts/{id}/blind")
    public ResponseEntity<Void> blind(@PathVariable Long id) {
        postModerationService.blindPost(id);
        return ResponseEntity.noContent().build();
    }

    /** 숨겨진 글의 숨김을 푼다. */
    @PostMapping("/api/v1/admin/posts/{id}/unblind")
    public ResponseEntity<Void> unblind(@PathVariable Long id) {
        postModerationService.unblindPost(id);
        return ResponseEntity.noContent().build();
    }

    /** 글을 요청한 기한까지 목록 상단에 고정한다. 이미 고정된 글이면 기한만 바꾼다. */
    @PutMapping("/api/v1/admin/posts/{id}/pin")
    public ResponseEntity<Void> pin(@PathVariable Long id, @Valid @RequestBody PostPinRequestDto requestDto) {
        postModerationService.pin(id, requestDto.pinnedUntil());
        return ResponseEntity.noContent().build();
    }

    /** 고정을 푼다. */
    @DeleteMapping("/api/v1/admin/posts/{id}/pin")
    public ResponseEntity<Void> unpin(@PathVariable Long id) {
        postModerationService.unpin(id);
        return ResponseEntity.noContent().build();
    }
}
