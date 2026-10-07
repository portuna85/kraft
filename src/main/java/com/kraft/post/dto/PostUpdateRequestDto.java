package com.kraft.post.dto;

import com.kraft.post.domain.Category;
import com.kraft.shared.domain.ContentPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PostUpdateRequestDto(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 255, message = "제목은 255자 이하로 입력하세요.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = ContentPolicy.POST_CONTENT_MAX_LENGTH, message = "내용은 {max}자 이하로 입력하세요.")
        String content,

        @Size(max = 500, message = "이미지 경로가 올바르지 않습니다.")
        String picture,

        // PostSaveRequestDto와 같은 이유(A-FE-09) — CLS 방지용 힌트일 뿐 서버가 picture와의
        // 정합성을 검증하지 않는다.
        @Positive(message = "이미지 폭은 양수여야 합니다.")
        Integer pictureWidth,

        @Positive(message = "이미지 높이는 양수여야 합니다.")
        Integer pictureHeight,

        Category category
        // 기준 버전은 본문이 아니라 If-Match 헤더로 받는다(EntityTags.expectedVersion, 컨트롤러). 옛 화면이 아직
        // 본문에 version을 실어 보내도 무시한다 — 알 수 없는 속성은 거절하지 않는다.
) {
}
