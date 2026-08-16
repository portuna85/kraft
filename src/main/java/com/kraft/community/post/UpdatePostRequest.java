package com.kraft.community.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 20000) String content,
        // I-23: 분류가 발행 후 영구 고정이라 잘못 분류한 글을 고칠 방법이 없었다.
        @NotBlank String category,
        @NotNull Long expectedVersion
) {
}
