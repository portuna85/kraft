package com.kraft.web.dto.post;

import jakarta.validation.constraints.NotBlank;

public record PostUpdateRequestDto(

        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content
) {
}
