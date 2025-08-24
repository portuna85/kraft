package com.boardly.post.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCreateForm(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content
) {
}
