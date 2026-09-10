package com.kraft.web.dto.post;

import com.kraft.domain.post.Post;

public record PostResponseDto(
        Long id,
        String title,
        String content,
        String picture,
        String author
) {

    public PostResponseDto(Post entity) {
        this(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getPicture(),
                entity.getUser() != null ? entity.getUser().getName() : null
        );
    }
}
