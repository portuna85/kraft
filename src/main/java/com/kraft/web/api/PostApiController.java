package com.kraft.web.api;

import com.kraft.service.post.PostImageService;
import com.kraft.service.post.PostService;
import com.kraft.web.dto.post.ImageUploadResponseDto;
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
    private final PostImageService postImageService;

    @PostMapping("/api/v1/posts")
    public Long save(@Valid @RequestBody PostSaveRequestDto requestDto, Authentication authentication) {
        return postService.save(authentication.getName(), requestDto);
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
    public PostsPageResponseDto findAll(@PageableDefault(size = 10) Pageable pageable) {
        return postService.findAllDesc(pageable);
    }

    @PostMapping(value = "/api/v1/posts/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageUploadResponseDto uploadImage(@RequestParam("file") MultipartFile file) {
        return new ImageUploadResponseDto(postImageService.store(file));
    }
}
