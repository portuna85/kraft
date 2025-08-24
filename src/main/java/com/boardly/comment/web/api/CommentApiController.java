package com.boardly.comment.web.api;

import com.boardly.comment.service.CommentService;
import com.boardly.core.response.ApiResponse;
import com.boardly.user.domain.User;
import com.boardly.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentApiController {
    private final CommentService commentService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal(expression = "username") String username,
                                    @RequestBody @Valid CommentCreateRequest req) {
        User user = userService.findByUsername(username);
        Long id = commentService.addComment(req.postId(), user, req.content());
        return ResponseEntity.ok(ApiResponse.ok(id));
    }
}
