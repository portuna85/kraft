package com.kraft.comment.dto;

import com.kraft.shared.domain.ContentPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequestDto(

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = ContentPolicy.COMMENT_CONTENT_MAX_LENGTH, message = "댓글은 {max}자 이하로 입력하세요.")
        String content,

        /**
         * 편집 화면이 받아간 시점의 댓글 버전(B12). 그 사이 다른 저장이 있었으면 지금 DB의
         * 버전과 달라 409로 충돌을 표면화한다. {@code PostUpdateRequestDto.version}과 같은 계약으로
         * 필수다(평가 보고서 2026-09-25 F11). 없으면 400이다.
         */
        @NotNull(message = "수정할 댓글의 버전 정보가 필요합니다. 화면을 새로고침한 뒤 다시 시도하세요.")
        Long version
) {
}
