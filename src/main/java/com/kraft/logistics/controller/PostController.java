package com.kraft.logistics.controller;

import com.kraft.logistics.domain.post.PostService;
import com.kraft.logistics.domain.post.dto.PostResponseDto;
import com.kraft.logistics.domain.post.dto.PostWriteRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    @PostMapping
    public PostResponseDto write(@RequestBody PostWriteRequestDto dto) {
        return postService.write(dto);
    }
}
