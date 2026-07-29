package com.kraft.community.comment;

import com.kraft.community.auth.CommunityPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommunityCommentController {

    private final CommunityCommentService communityCommentService;

    public CommunityCommentController(CommunityCommentService communityCommentService) {
        this.communityCommentService = communityCommentService;
    }

    @GetMapping("/api/v1/community/posts/{postId}/comments")
    public ResponseEntity<CommunityCommentPageResponse> list(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + CommunityCommentService.DEFAULT_PAGE_SIZE) int size) {
        CommunityCommentListResult result = communityCommentService.list(postId, page, size);
        List<CommunityCommentResponse> items = result.topLevel().getContent().stream()
                .map(comment -> CommunityCommentResponse.from(comment,
                        result.repliesByParentId().getOrDefault(comment.getId(), List.of()).stream()
                                .map(CommunityCommentResponse::from)
                                .toList()))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CommunityCommentPageResponse(
                        items,
                        result.topLevel().getTotalElements(),
                        result.topLevel().getNumber(),
                        result.topLevel().getSize(),
                        result.topLevel().getTotalPages()));
    }

    @PostMapping("/api/v1/community/posts/{postId}/comments")
    public ResponseEntity<CommunityCommentResponse> create(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommunityCommentCreationResult result = communityCommentService.create(
                principal.getUserId(), principal.getNickname(), postId, request);
        return ResponseEntity.status(201)
                .body(CommunityCommentResponse.from(result.comment(), result.targetPage()));
    }

    @DeleteMapping("/api/v1/community/comments/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @PathVariable Long id) {
        communityCommentService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
