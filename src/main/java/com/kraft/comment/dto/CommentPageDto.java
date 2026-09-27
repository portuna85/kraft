package com.kraft.comment.dto;

import java.util.List;

/**
 * 댓글 커서 페이지 응답. {@code totalCount}는 화면이 "더 보기" 없이도 전체 개수를 보여줄 수
 * 있게 별도로 담는다(로드된 {@code comments.size()}와는 독립적).
 * <p>
 * 후속 페이지(커서 {@code afterId}가 있는 호출)에서는 {@code null}이다(전체 리뷰 2026-09-26
 * A-BE-13) — 전체 개수는 최초 페이지에서 이미 받았고, 그 뒤로는 등록·삭제 때마다 화면
 * ({@code CommentsApp.vue})이 로컬에서 정확히 증감시키고 있어 매번 다시 셀 이유가 없다.
 * 화면은 이 값이 null이면 기존 값을 그대로 유지한다.
 */
public record CommentPageDto(
        List<CommentViewDto> comments,
        Long totalCount,
        boolean hasMore
) {
}
