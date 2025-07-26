package com.kraft.logistics.domain.comment.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentUpdateRequestDto {
    private Long commentId;
    private Long userId;     // 수정 권한 확인용
    private String content;  // 수정할 내용
}
