package com.kraft.community.post;

import com.kraft.common.error.ApiException;
import com.kraft.common.web.DeviceTokenSupport;
import com.kraft.community.auth.CommunityPrincipal;
import com.kraft.community.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/community/posts")
public class CommunityPostController {

    private final CommunityPostService communityPostService;
    private final DeviceTokenSupport deviceTokenSupport;

    public CommunityPostController(CommunityPostService communityPostService,
                                    DeviceTokenSupport deviceTokenSupport) {
        this.communityPostService = communityPostService;
        this.deviceTokenSupport = deviceTokenSupport;
    }

    @GetMapping
    public ResponseEntity<PageResponse<CommunityPostResponse>> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PostCategory parsedCategory = category == null || category.isBlank() ? null : parseCategoryParam(category);
        Page<CommunityPost> result = communityPostService.list(parsedCategory, sort, query, page, size);
        return ResponseEntity.ok().body(PageResponse.from(communityPostService.toResponsePage(result)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommunityPostResponse> detail(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @PathVariable Long id) {
        Long requesterId = principal == null ? null : principal.getUserId();
        CommunityPost post = communityPostService.get(id, requesterId);
        return ResponseEntity.ok()
                .eTag(String.valueOf(post.getVersion()))
                .body(communityPostService.toResponse(post));
    }

    @PostMapping
    public ResponseEntity<CommunityPostResponse> create(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @RequestHeader(name = "X-Device-Token", required = false) String deviceToken,
            @Valid @RequestBody CreatePostRequest request) {
        String clientTokenHash = deviceToken == null || deviceToken.isBlank()
                ? null
                : deviceTokenSupport.requireHashedToken(deviceToken);
        CommunityPost post = communityPostService.create(
                principal.getUserId(), principal.getNickname(), clientTokenHash, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(post.getId())
                .toUri();
        return ResponseEntity.created(location)
                .eTag(String.valueOf(post.getVersion()))
                .body(communityPostService.toResponse(post));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommunityPostResponse> update(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePostRequest request) {
        CommunityPost post = communityPostService.update(principal.getUserId(), id, request);
        return ResponseEntity.ok()
                .eTag(String.valueOf(post.getVersion()))
                .cacheControl(CacheControl.noStore())
                .body(communityPostService.toResponse(post));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @PathVariable Long id,
            @RequestParam long expectedVersion) {
        communityPostService.delete(principal.getUserId(), id, expectedVersion);
        return ResponseEntity.noContent().build();
    }

    private static PostCategory parseCategoryParam(String category) {
        try {
            return PostCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_CATEGORY_INVALID",
                    "지원하지 않는 카테고리입니다: " + category);
        }
    }
}
