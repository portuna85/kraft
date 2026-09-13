package com.kraft.web.dto.post;

import com.kraft.domain.ContentPolicy;
import com.kraft.domain.post.Category;
import jakarta.validation.constraints.NotBlank;
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
         * 다른 곳에서 저장이 일어난 것이므로 409로 거절한다(덮어쓰기 방지). null이면 검사하지
         * 않는다 — 버전을 싣지 않는 요청의 기존 동작을 그대로 둔다.
         */
        Long version
) {
}
