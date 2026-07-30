package com.kraft.community.reaction;

import com.kraft.community.auth.CommunityPrincipal;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community")
@Validated
public class CommunityReactionController {

    private final CommunityReactionService communityReactionService;

    public CommunityReactionController(CommunityReactionService communityReactionService) {
        this.communityReactionService = communityReactionService;
    }

    @PutMapping("/posts/{id}/like")
    public ResponseEntity<Void> like(@AuthenticationPrincipal CommunityPrincipal principal, @PathVariable Long id) {
        communityReactionService.like(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{id}/like")
    public ResponseEntity<Void> unlike(@AuthenticationPrincipal CommunityPrincipal principal, @PathVariable Long id) {
        communityReactionService.unlike(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/posts/{id}/bookmark")
    public ResponseEntity<Void> bookmark(@AuthenticationPrincipal CommunityPrincipal principal,
                                          @PathVariable Long id) {
        communityReactionService.bookmark(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{id}/bookmark")
    public ResponseEntity<Void> unbookmark(@AuthenticationPrincipal CommunityPrincipal principal,
                                            @PathVariable Long id) {
        communityReactionService.unbookmark(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    // KB-10: 상한 없이 postIds를 그대로 IN 절 두 개(좋아요·북마크)에 실어 보내던 것을 막는다.
    @GetMapping("/me/interactions")
    public ResponseEntity<CommunityInteractionsResponse> interactions(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @RequestParam @Size(max = 100) List<Long> postIds) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(communityReactionService.interactions(principal.getUserId(), postIds));
    }
}
