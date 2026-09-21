package com.kraft.comment.dto;

import com.kraft.shared.domain.ContentPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequestDto(

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = ContentPolicy.COMMENT_CONTENT_MAX_LENGTH, message = "댓글은 {max}자 이하로 입력하세요.")
        String content,

        /**
         * 편집 화면이 받아간 시점의 댓글 버전(B12). 그 사이 다른 저장이 있었으면 지금 DB의
         * 버전과 달라 409로 충돌을 표면화한다. {@code Post}의 nullable 버전 계약과 같다 —
         * 보내지 않으면(null) 기존처럼 검사 없이 그대로 저장한다.
         */
        Long version
) {
}
