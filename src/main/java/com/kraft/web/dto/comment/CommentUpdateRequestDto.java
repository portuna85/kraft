package com.kraft.web.dto.comment;

import com.kraft.domain.ContentPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequestDto(

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = ContentPolicy.COMMENT_CONTENT_MAX_LENGTH, message = "댓글은 {max}자 이하로 입력하세요.")
        String content
) {
}
