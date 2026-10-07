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
        long viewCount,
        /** 낙관적 잠금 버전. GET 응답의 ETag로도 실려 수정 요청의 {@code If-Match}가 된다. */
        Long version
) {

    public PostResponseDto(Post entity) {
        this(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getPicture(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                entity.getCategory(),
                entity.getViewCount(),
                entity.getVersion()
        );
    }
}
