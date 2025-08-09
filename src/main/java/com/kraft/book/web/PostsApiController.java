package com.kraft.book.web;

import ch.qos.logback.core.model.Model;
import com.kraft.book.service.PostsService;
import com.kraft.book.web.dto.PostsResponseDto;
import com.kraft.book.web.dto.PostsSaveRequestDto;
import com.kraft.book.web.dto.PostsUpdateRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RequiredArgsConstructor
@RestController
public class PostsApiController {

    private final PostsService postsService;

    @PostMapping("/api/v1/posts")
    public ResponseEntity<?> save(@Valid @RequestBody PostsSaveRequestDto req) {
        Long id = postsService.save(req);
        URI location = URI.create("/api/v1/posts/" + id);
        return ResponseEntity.created(location).build(); // 201 Created
    }


    @PutMapping("/api/v1/posts/{id}")
    public Long update(@PathVariable Long id, @RequestBody PostsUpdateRequestDto requestDto) {
        return postsService.update(id, requestDto);
    }

    @DeleteMapping("/api/v1/posts/{id}")
    public Long delete(@PathVariable Long id) {
        postsService.delete(id);
        return id;
    }

    @GetMapping("/api/v1/posts/{id}")
    public PostsResponseDto findById(@PathVariable Long id) {

        return postsService.findById(id);
    }


}
