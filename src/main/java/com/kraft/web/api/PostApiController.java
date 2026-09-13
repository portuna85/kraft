package com.kraft.web.api;

import com.kraft.domain.post.Category;
import com.kraft.service.post.PostService;
import com.kraft.web.dto.post.ImageUploadResponseDto;
import com.kraft.web.dto.post.PostLikeResponseDto;
import com.kraft.web.dto.post.PostResponseDto;
import com.kraft.web.dto.post.PostSaveRequestDto;
import com.kraft.web.dto.post.PostUpdateRequestDto;
import com.kraft.web.dto.post.PostsPageResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
public class PostApiController {

    private final PostService postService;

    @PostMapping("/api/v1/posts")
    public Long save(@Valid @RequestBody PostSaveRequestDto requestDto, Authentication authentication) {
        return postService.save(authentication, requestDto);
    }

    @PutMapping("/api/v1/posts/{id}")
    public Long update(@PathVariable Long id, @Valid @RequestBody PostUpdateRequestDto requestDto,
                        Authentication authentication) {
        return postService.update(id, requestDto, authentication);
    }

    @DeleteMapping("/api/v1/posts/{id}")
    public Long delete(@PathVariable Long id, Authentication authentication) {
        postService.delete(id, authentication);
        return id;
    }

    @GetMapping("/api/v1/posts/{id}")
    public PostResponseDto findById(@PathVariable Long id) {
        return postService.findById(id);
    }

    @GetMapping("/api/v1/posts")
    public PostsPageResponseDto findAll(@PageableDefault(size = 10) Pageable pageable,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) Category category) {
        return postService.findAllDesc(pageable, q, category);
    }

    /**
     * 업로드는 저장한 파일을 업로더와 함께 대장에 기록해야 하므로(소유권 검사의 근거)
     * 파일 저장만 하는 {@code PostImageService} 대신 {@code PostService}를 거친다.
     */
    @PostMapping(value = "/api/v1/posts/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageUploadResponseDto uploadImage(@RequestParam("file") MultipartFile file,
                                               Authentication authentication) {
        return new ImageUploadResponseDto(postService.uploadImage(file, authentication));
    }

    @PutMapping("/api/v1/posts/{id}/like")
    public PostLikeResponseDto toggleLike(@PathVariable Long id, Authentication authentication) {
        return postService.toggleLike(id, authentication);
    }
}
