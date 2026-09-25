package com.kraft.post.dto;

import com.kraft.post.domain.Category;
import com.kraft.shared.domain.ContentPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

        Category category,

        /**
         * 편집을 시작할 때 화면이 받아간 게시글 버전. 저장 시점에 DB의 버전과 다르면 그 사이
         * 다른 곳에서 저장이 일어난 것이므로 409로 거절한다(덮어쓰기 방지).
         * <p>
         * 필수다(평가 보고서 2026-09-25 F11) — 예전에는 null이면 검사를 건너뛰어, 버전을 싣지
         * 않은 요청은 오래된 화면에서 보낸 것이어도 그대로 덮어썼다. 웹 UI 외의 API 클라이언트는
         * 없음을 확인하고 필수로 바꿨다. 없으면 400이다.
         */
        @NotNull(message = "수정할 글의 버전 정보가 필요합니다. 화면을 새로고침한 뒤 다시 시도하세요.")
        Long version
) {
}
