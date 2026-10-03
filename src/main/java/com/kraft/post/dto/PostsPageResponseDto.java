package com.kraft.post.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * 게시글 목록 페이징 응답.
 * Spring Data의 {@link Page}를 그대로 직렬화하지 않고, API 계약을 명시적으로 고정하기 위해
 * 필요한 필드만 담은 record로 감싼다.
 * <p>
 * 검색어가 있는 요청은 전체 건수를 세지 않으므로(BE-08) {@code totalElements}·{@code totalPages}가
 * null이다. 호출하는 쪽이 "총 몇 개"·번호 이동을 그릴 수 있는지는 이 값의 null 여부로 판단한다.
 */
public record PostsPageResponseDto(
        List<PostsListResponseDto> content,
        int page,
        int size,
        /** 검색어가 있으면 null — 전체 건수를 세지 않는다(BE-08). 검색어 없는 목록은 항상 값이 있다. */
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

    /**
     * 전체 건수를 모르는 응답(검색). 다음 페이지가 있는지({@code last})만 안다. {@code totalElements}·
     * {@code totalPages}는 null이다 — 모르는 값을 0이나 -1 같은 숫자로 위장하지 않는다.
     */
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
