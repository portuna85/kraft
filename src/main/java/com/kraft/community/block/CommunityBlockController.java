package com.kraft.community.block;

import com.kraft.community.auth.CommunityPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community/users/{id}/block")
public class CommunityBlockController {

    private final CommunityBlockService communityBlockService;

    public CommunityBlockController(CommunityBlockService communityBlockService) {
        this.communityBlockService = communityBlockService;
    }

    @PutMapping
    public ResponseEntity<Void> block(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @PathVariable Long id) {
        communityBlockService.block(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unblock(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @PathVariable Long id) {
        communityBlockService.unblock(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
