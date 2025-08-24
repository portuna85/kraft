package com.boardly.user.web.api;


import com.boardly.core.response.ApiResponse;
import com.boardly.user.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


record SignUpRequest(
        @NotBlank @Size(max = 30) String username,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 30) String nickname,
        @Email @NotBlank String email
) {
}
