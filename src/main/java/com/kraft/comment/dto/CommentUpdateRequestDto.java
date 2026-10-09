package com.kraft.comment.dto;

import com.kraft.shared.domain.ContentPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequestDto(

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = ContentPolicy.COMMENT_CONTENT_MAX_LENGTH, message = "댓글은 {max}자 이하로 입력하세요.")
        String content
        // 편집 화면이 받아간 시점의 댓글 버전은 본문이 아니라 If-Match 헤더로 받는다
        // (PostUpdateRequestDto와 같다).
) {
}
