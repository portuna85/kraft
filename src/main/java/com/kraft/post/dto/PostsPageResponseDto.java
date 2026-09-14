package com.kraft.post.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 게시글 목록 페이징 응답.
 * Spring Data의 {@link Page}를 그대로 직렬화하지 않고, API 계약을 명시적으로 고정하기 위해
 * 필요한 필드만 담은 record로 감싼다.
 */
public record PostsPageResponseDto(
        List<PostsListResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public PostsPageResponseDto(Page<PostsListResponseDto> page) {
        this(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
