package com.boardly.post.web.dto;

import jakarta.validation.constraints.*;

public record PostCreateRequest(
        @NotBlank @Size(max=200) String title,
        @NotBlank String content
) {}