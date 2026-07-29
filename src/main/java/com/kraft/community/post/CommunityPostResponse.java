package com.kraft.community.post;

import java.time.OffsetDateTime;

// owner 판정에 필요한 ownerId는 포함하되, canEdit 같은 개인화 파생 필드는 넣지 않는다 —
// 소유권 판정은 클라이언트가 세션 엔드포인트의 로그인 사용자 ID와 대조해 스스로 계산한다.
// likeCount/commentCount/viewCount는 community_post_metrics(단일 진실 공급원)에서 온다.
public record CommunityPostResponse(
        Long id,
        Long ownerId,
        String authorNickname,
        String title,
        String content,
        PostCategory category,
        PostStatus status,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int likeCount,
        int commentCount,
        long viewCount,
        RecommendationAttachmentView recommendationAttachment
) {

    public static CommunityPostResponse from(CommunityPost post, CommunityPostMetrics metrics,
                                              RecommendationAttachmentView attachment) {
        return new CommunityPostResponse(
                post.getId(),
                post.getOwnerId(),
                post.getAuthorNameSnapshot(),
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                post.getStatus(),
                post.getVersion(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                metrics == null ? 0 : metrics.getLikeCount(),
                metrics == null ? 0 : metrics.getCommentCount(),
                metrics == null ? 0 : metrics.getViewCount(),
                attachment);
    }
}
