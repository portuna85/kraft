package com.kraft.community.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record CommunityCommentResponse(
        Long id,
        Long postId,
        @Schema(nullable = true) Long parentId,
        @Schema(nullable = true) Long ownerId,
        String authorNickname,
        String content,
        boolean deleted,
        OffsetDateTime createdAt,
        @Schema(nullable = true) Integer targetPage,
        List<CommunityCommentResponse> replies
) {

    private static final String TOMBSTONE_CONTENT = "삭제된 댓글입니다.";
    private static final String TOMBSTONE_AUTHOR = "(삭제됨)";

    // tombstone된 댓글은 행은 유지하되 프런트에는 마스킹된 값만 노출한다 — 삭제된
    // 댓글은 소유권 판정 UI 자체를 프런트에서 숨기므로(항상 액션 버튼 비노출) ownerId도
    // 함께 감춘다(Blitz의 "authorUserId를 노출하지 않는다"와 동일 수준의 정보 최소화).
    public static CommunityCommentResponse from(CommunityComment comment) {
        return from(comment, null, List.of());
    }

    // targetPage는 작성 직후 응답에만 채운다 — 목록 조회 응답에서는 항목마다 계산할
    // 이유가 없어 null로 둔다.
    public static CommunityCommentResponse from(CommunityComment comment, Integer targetPage) {
        return from(comment, targetPage, List.of());
    }

    // 상위 댓글 목록 응답에서 답글(depth 1)을 중첩해 내려줄 때 사용한다 — 답글 자체는
    // 답글을 가질 수 없으므로(1단계 제한) 재귀 위험이 없다.
    public static CommunityCommentResponse from(CommunityComment comment, List<CommunityCommentResponse> replies) {
        return from(comment, null, replies);
    }

    private static CommunityCommentResponse from(CommunityComment comment, Integer targetPage,
                                                  List<CommunityCommentResponse> replies) {
        boolean deleted = comment.isDeleted();
        return new CommunityCommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getParentId(),
                deleted ? null : comment.getOwnerId(),
                deleted ? TOMBSTONE_AUTHOR : comment.getAuthorNameSnapshot(),
                deleted ? TOMBSTONE_CONTENT : comment.getContent(),
                deleted,
                comment.getCreatedAt(),
                targetPage,
                replies);
    }
}
