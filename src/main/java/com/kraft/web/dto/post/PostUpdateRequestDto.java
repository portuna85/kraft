package com.kraft.web.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostUpdateRequestDto(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 255, message = "제목은 255자 이하로 입력하세요.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content
) {
}
