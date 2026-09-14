package com.kraft.post.dto;

import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
public record PostResponseDto(
        Long id,
        String title,
        String content,
        String picture,
        String author,
        Category category,
        long viewCount
) {

    public PostResponseDto(Post entity) {
        this(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getPicture(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                entity.getCategory(),
                entity.getViewCount()
        );
    }
}
