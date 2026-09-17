package com.kraft.post.dto;

import com.kraft.post.domain.Category;
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

    public PostsListResponseDto(PostRowDto row, long commentCount) {
        this(row.id(), row.title(), row.author(), row.modifiedDate(), row.category(), row.viewCount(), commentCount);
    }
}
