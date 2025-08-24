package com.boardly.user.web.dto;

import jakarta.validation.constraints.*;

public record SignUpForm(
        @NotBlank @Size(max = 30) String username,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 30) String nickname,
        @Email @NotBlank String email
) {
}