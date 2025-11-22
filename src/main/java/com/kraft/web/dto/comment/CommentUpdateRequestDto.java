package com.kraft.web.dto.comment;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글 수정 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentUpdateRequestDto {

    @NotBlank(message = "댓글 내용은 필수입니다")
    private String content;
}

