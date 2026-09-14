package com.kraft.comment.dto;

import com.kraft.comment.domain.Comment;
import java.time.LocalDateTime;

public record CommentResponseDto(
        Long id,
        Long postId,
        String content,
        String author,
        LocalDateTime createdAt
) {

    public CommentResponseDto(Comment entity) {
        this(
                entity.getId(),
                entity.getPost() != null ? entity.getPost().getId() : null,
                entity.getContent(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                entity.getCreatedAt()
        );
    }
}
