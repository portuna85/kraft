package com.kraft.web.dto.post;

import com.kraft.domain.post.Post;

import java.time.LocalDateTime;

public record PostsListResponseDto(
        Long id,
        String title,
        String author,
        LocalDateTime modifiedDate
) {

    public PostsListResponseDto(Post entity) {
        this(
                entity.getId(),
                entity.getTitle(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                entity.getUpdatedAt()
        );
    }
}
