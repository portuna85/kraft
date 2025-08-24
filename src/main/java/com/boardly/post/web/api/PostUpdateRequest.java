package com.boardly.post.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record PostUpdateRequest(@NotBlank @Size(max = 200) String title, @NotBlank String content) {
}