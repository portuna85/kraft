package com.kraft.comment.dto;

import com.kraft.comment.domain.Comment;
import java.time.LocalDateTime;

/**
 * 댓글 <b>화면 전용</b> 응답. 공개 REST 응답({@link CommentResponseDto})에는 권한 필드를
 * 추가하지 않고, 화면에만 댓글별 {@code canManage}를 내려 관리 버튼 노출을 서버 판정에 맞춘다.
 */
public record CommentViewDto(
        Long id,
        Long postId,
        String content,
        String author,
        LocalDateTime createdAt,
        boolean canManage
) {

    public CommentViewDto(Comment entity, boolean canManage) {
        this(
                entity.getId(),
                entity.getPost() != null ? entity.getPost().getId() : null,
                entity.getContent(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                entity.getCreatedAt(),
                canManage
        );
    }
}
