package com.kraft.post.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

import java.util.List;

/** 게시글 목록 페이징 응답 — Spring Data {@link Page}를 그대로 직렬화하지 않고 필요한 필드만 담아 API 계약을 고정한다. 검색어가 있으면 전체 건수를 세지 않아 {@code totalElements}·{@code totalPages}가 null이다. */
public record PostsPageResponseDto(
        List<PostsListResponseDto> content,
        int page,
        int size,
        /** 검색어가 있으면 null — 전체 건수를 세지 않는다. 검색어 없는 목록은 항상 값이 있다. */
        Long totalElements,
        /** {@code totalElements}와 같이 검색어가 있으면 null. */
        Integer totalPages,
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

    /** 전체 건수를 모르는 응답(검색): 다음 페이지 유무({@code last})만 안다. 모르는 값을 0이나 -1로 위장하지 않고 null로 둔다. */
    public PostsPageResponseDto(Slice<PostsListResponseDto> slice) {
        this(
                slice.getContent(),
                slice.getNumber(),
                slice.getSize(),
                null,
                null,
                slice.isFirst(),
                slice.isLast()
        );
    }
}
