package com.boardly.post.web.api;

import com.boardly.core.response.ApiResponse;
import com.boardly.post.domain.Post;
import com.boardly.post.service.PostService;
import com.boardly.user.domain.User;
import com.boardly.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

record PostCreateRequest(@NotBlank @Size(max=200) String title, @NotBlank String content) { }


