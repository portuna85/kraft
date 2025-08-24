package com.boardly.comment.web.api;

import com.boardly.core.response.ApiResponse;
import com.boardly.comment.service.CommentService;
import com.boardly.user.domain.User;
import com.boardly.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

record CommentCreateRequest(@NotNull Long postId, @NotBlank String content) {
}

