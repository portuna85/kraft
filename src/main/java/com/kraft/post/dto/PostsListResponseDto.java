package com.kraft.post.dto;

import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import java.time.LocalDateTime;

public record PostsListResponseDto(
        Long id,
        String title,
        String author,
        LocalDateTime modifiedDate,
        Category category,
        long viewCount,
        long commentCount
) {

    public PostsListResponseDto(Post entity, long commentCount) {
        this(
                entity.getId(),
                entity.getTitle(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                entity.getUpdatedAt(),
                entity.getCategory(),
                entity.getViewCount(),
                commentCount
        );
    }
}
