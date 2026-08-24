package com.kraft.community.post;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.common.web.DeviceTokenSupport;
import com.kraft.common.web.PageResponse;
import com.kraft.community.auth.CommunityPrincipal;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
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
        PostSort parsedSort = parseSortParam(sort);
        Page<CommunityPost> result = communityPostService.list(parsedCategory, parsedSort, query, page, size);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(communityPostService.toResponsePage(result)));
    }

    /**
     * KB-03: ETag를 version으로 걸었던 이전 구현은 응답 본문에 like/comment/view count처럼
     * version과 무관하게 바뀌는 휘발성 지표가 섞여 있어, 글 수정 없이 반응만 바뀐 사이에
     * 브라우저가 304로 옛 지표를 계속 보여줄 수 있었다. KB-11: 목록·댓글과 마찬가지로
     * 명시적 no-store로 통일한다(휴리스틱 캐시 제거).
     */
    @GetMapping("/{id}")
    public ResponseEntity<CommunityPostResponse> detail(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @PathVariable Long id) {
        Long requesterId = principal == null ? null : principal.getUserId();
        CommunityPost post = communityPostService.get(id, requesterId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(communityPostService.toResponse(post));
    }

    @PostMapping
    @ApiResponse(responseCode = "201")
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
    @ApiResponse(responseCode = "204")
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
            throw new ApiException(ApiErrorCode.COMMUNITY_CATEGORY_INVALID,
                    "지원하지 않는 카테고리입니다: " + category);
        }
    }

    // API-COMM-01(docs/improvement.md): null/blank만 latest로 기본 처리하고, 그 외 알 수 없는
    // 값은 조용히 latest로 흡수하지 않고 400으로 거부한다.
    private static PostSort parseSortParam(String sort) {
        if (sort == null || sort.isBlank()) {
            return PostSort.LATEST;
        }
        try {
            return PostSort.valueOf(sort.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.INVALID_SORT, "지원하지 않는 정렬입니다: " + sort);
        }
    }
}
