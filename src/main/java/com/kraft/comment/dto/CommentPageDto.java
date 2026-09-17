package com.kraft.comment.dto;

import java.util.List;

/**
 * 댓글 커서 페이지 응답. {@code totalCount}는 화면이 "더 보기" 없이도 전체 개수를 보여줄 수
 * 있게 별도로 담는다(로드된 {@code comments.size()}와는 독립적).
 */
public record CommentPageDto(
        List<CommentViewDto> comments,
        long totalCount,
        boolean hasMore
) {
}
