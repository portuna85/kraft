package com.boardly.post.web.api;

import com.boardly.core.response.ApiResponse;
import com.boardly.post.domain.Post;
import com.boardly.post.service.PostService;
import com.boardly.user.domain.User;
import com.boardly.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostApiController {
    private final PostService postService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal(expression="username") String username,
                                    @RequestBody @Valid PostCreateRequest req) {
        User user = userService.findByUsername(username);
        Long id = postService.create(user, req.title(), req.content());
        return ResponseEntity.ok(ApiResponse.ok(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        Post post = postService.get(id);
        return ResponseEntity.ok(ApiResponse.ok(PostResponse.from(post)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody @Valid PostUpdateRequest req) {
        postService.update(id, req.title(), req.content());
        return ResponseEntity.ok(ApiResponse.ok(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(id));
    }
}
