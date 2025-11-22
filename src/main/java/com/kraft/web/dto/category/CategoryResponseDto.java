package com.kraft.web.dto.category;

import com.kraft.domain.category.Category;

/**
 * 카테고리 응답 DTO
 */
public record CategoryResponseDto(
        Long id,
        String name,
        String description,
        int displayOrder
) {
    public static CategoryResponseDto from(Category category) {
        return new CategoryResponseDto(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getDisplayOrder()
        );
    }
}

