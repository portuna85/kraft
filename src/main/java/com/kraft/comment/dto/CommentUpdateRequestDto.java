package com.kraft.comment.dto;

import com.kraft.shared.domain.ContentPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequestDto(

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = ContentPolicy.COMMENT_CONTENT_MAX_LENGTH, message = "댓글은 {max}자 이하로 입력하세요.")
        String content,

        /**
         * 편집 화면이 받아간 시점의 댓글 버전(B12). {@code PostUpdateRequestDto.version}과 같은 이유로 기준 버전은
         * {@code If-Match} 헤더로 보내고, 이 필드는 배포 전에 열어 둔 탭을 위한 <b>전환기 폴백</b>이다. 헤더도 이
         * 값도 없으면 428이다. 다음 릴리스에서 이 필드를 지운다.
         */
        Long version
) {
}
