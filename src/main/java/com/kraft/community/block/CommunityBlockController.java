package com.kraft.community.block;

import com.kraft.community.auth.CommunityPrincipal;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community")
public class CommunityBlockController {

    private final CommunityBlockService communityBlockService;

    public CommunityBlockController(CommunityBlockService communityBlockService) {
        this.communityBlockService = communityBlockService;
    }

    @PutMapping("/users/{id}/block")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> block(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @PathVariable Long id) {
        communityBlockService.block(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/block")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> unblock(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @PathVariable Long id) {
        communityBlockService.unblock(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * C-1: 프론트가 페이지 로드 시 한 번만 조회해 피드·상세·댓글 렌더링과 차단 버튼 초기
     * 상태를 함께 걸러내는 데 쓴다. 기존 {@code /me/interactions}는 postIds가 필수라 이
     * 용도로 재사용할 수 없었다(빈 배열을 보내면 요청 파라미터 자체가 없어져 400이 난다).
     */
    @GetMapping("/me/blocked-users")
    public ResponseEntity<List<Long>> blockedUsers(@AuthenticationPrincipal CommunityPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(communityBlockService.blockedUserIds(principal.getUserId()));
    }
}
