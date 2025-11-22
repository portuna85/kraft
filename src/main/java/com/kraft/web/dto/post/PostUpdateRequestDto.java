package com.kraft.web.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PostUpdateRequestDto {

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 500, message = "제목은 500자 이하여야 합니다")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    private String content;
}