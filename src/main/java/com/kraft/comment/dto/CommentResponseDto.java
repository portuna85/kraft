package com.kraft.comment.dto;

import com.kraft.comment.domain.Comment;
import java.time.LocalDateTime;

/** parentId가 null이면 최상위 댓글, 있으면 그 id의 댓글에 대한 답글이다(2단계 댓글). */
public record CommentResponseDto(
        Long id,
        Long postId,
        Long parentId,
        String content,
        String author,
        LocalDateTime createdAt
) {

    public CommentResponseDto(Comment entity) {
        this(
                entity.getId(),
                entity.getPost() != null ? entity.getPost().getId() : null,
                entity.getParent() != null ? entity.getParent().getId() : null,
                entity.getContent(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                entity.getCreatedAt()
        );
    }
}
