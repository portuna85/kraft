package com.kraft.web.dto.common;

import java.util.List;

/**
 * 페이지네이션 응답 DTO
 * Record 클래스로 불변성과 간결성 보장
 */
public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean hasNext,
        boolean hasPrevious
) {
    public static <T> PageResponse<T> of(
            List<T> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages
    ) {
        return new PageResponse<>(
                content,
                pageNumber,
                pageSize,
                totalElements,
                totalPages,
                pageNumber == 0,
                pageNumber == totalPages - 1,
                pageNumber < totalPages - 1,
                pageNumber > 0
        );
    }
}

