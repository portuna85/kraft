package com.kraft.web.dto.post;

import com.kraft.domain.post.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostUpdateRequestDto(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 255, message = "제목은 255자 이하로 입력하세요.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content,

        @Size(max = 500, message = "이미지 경로가 올바르지 않습니다.")
        String picture,

        Category category
) {
}
