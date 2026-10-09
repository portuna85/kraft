package com.kraft.post.web;

import com.kraft.post.domain.Category;
import com.kraft.post.dto.ImageUploadResponseDto;
import com.kraft.post.dto.PostLikeRequestDto;
import com.kraft.post.dto.PostLikeResponseDto;
import com.kraft.post.dto.PostResponseDto;
import com.kraft.post.dto.PostSaveRequestDto;
import com.kraft.post.dto.PostUpdateRequestDto;
import com.kraft.post.service.PostQueryService;
import com.kraft.post.service.PostService;
import com.kraft.post.service.PostSortPolicy;
import com.kraft.shared.web.EntityTags;
import com.kraft.shared.web.RateLimitResponses;
import com.kraft.shared.web.WriteRateLimiters;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
public class PostApiController {

    private final PostService postService;
    private final PostQueryService postQueryService;
    private final WriteRateLimiters rateLimiters;

    /** 작성은 분당·시간당 두 창으로 속도를 제한한다({@code WriteRateLimiters}) — 이메일 인증만 통과하면 봇 하나로 게시판을 덮을 수 있다. */
    @PostMapping("/api/v1/posts")
    public ResponseEntity<?> save(@Valid @RequestBody PostSaveRequestDto requestDto, Authentication authentication) {
        if (!rateLimiters.tryAcquirePost(authentication)) {
            return RateLimitResponses.tooManyRequests("POST_RATE_LIMITED", 60);
        }
        return ResponseEntity.ok(postService.save(authentication, requestDto));
    }

    /** 수정은 {@code If-Match}로 기준 버전을 밝혀야 한다(다르면 412, 없으면 428). 응답 ETag는 저장 뒤의 새 버전이다. */
    @PutMapping("/api/v1/posts/{id}")
    public ResponseEntity<Long> update(@PathVariable Long id, @Valid @RequestBody PostUpdateRequestDto requestDto,
                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                        Authentication authentication) {
        PostService.PostUpdateResult result = postService.update(
                id, requestDto, EntityTags.expectedVersion(ifMatch), authentication);
        return ResponseEntity.ok().eTag(EntityTags.of(result.version())).body(result.id());
    }

    @DeleteMapping("/api/v1/posts/{id}")
    public Long delete(@PathVariable Long id, Authentication authentication) {
        postService.delete(id, authentication);
        return id;
    }

    /** ETag는 글의 버전이다 — 수정 요청의 {@code If-Match}로 그대로 돌려보낼 수 있다. */
    @GetMapping("/api/v1/posts/{id}")
    public ResponseEntity<PostResponseDto> findById(@PathVariable Long id) {
        PostResponseDto body = postQueryService.findById(id);
        return ResponseEntity.ok().eTag(EntityTags.of(body.version())).body(body);
    }

    /** "더 보기"(load-more.js)도 검색어를 실을 수 있어, SSR 검색({@code PostPageController.index})과 같은 IP 기준 속도 제한을 건다. */
    @GetMapping("/api/v1/posts")
    public ResponseEntity<?> findAll(@PageableDefault(size = 10) Pageable pageable,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) Category category,
                                      @RequestParam(required = false) String scope,
                                      HttpServletRequest request) {
        if (q != null && !q.isBlank() && !rateLimiters.tryAcquireSearch(request.getRemoteAddr())) {
            return RateLimitResponses.tooManyRequests("SEARCH_RATE_LIMITED", 60);
        }
        PostSortPolicy.validate(pageable.getSort());
        return ResponseEntity.ok(postQueryService.findAllDesc(pageable, q, category, SearchScope.isContent(scope)));
    }

    /** 업로드는 업로더를 대장에 기록해야 하므로(소유권 검사의 근거) 파일 저장만 하는 {@code PostImageService}가 아니라 {@code PostService}를 거친다. */
    @PostMapping(value = "/api/v1/posts/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file,
                                          Authentication authentication) {
        if (!rateLimiters.tryAcquireUpload(authentication)) {
            return RateLimitResponses.tooManyRequests("UPLOAD_RATE_LIMITED", 60);
        }
        return ResponseEntity.ok(new ImageUploadResponseDto(postService.uploadImage(file, authentication)));
    }

    /** 추천 상태를 요청한 최종 값으로 맞춘다(토글이 아니라 멱등). */
    @PutMapping("/api/v1/posts/{id}/like")
    public PostLikeResponseDto setLike(@PathVariable Long id,
                                        @Valid @RequestBody PostLikeRequestDto requestDto,
                                        Authentication authentication) {
        return postService.setLike(id, requestDto.liked(), authentication);
    }
}
