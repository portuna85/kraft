package com.kraft.community.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 좋아요·댓글·조회 집계의 단일 진실 공급원. 값 갱신은 이 엔티티를 읽어
 * 고쳐 쓰는 방식이 아니라 항상 {@link CommunityPostMetricsRepository}의 원자적 UPDATE
 * 쿼리로만 이뤄진다 — 동시 좋아요·댓글 생성에서 갱신 유실을 막기 위함이다.
 */
@Entity
@Table(name = "community_post_metrics")
public class CommunityPostMetrics {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CommunityPostMetrics() {
    }

    public CommunityPostMetrics(Long postId, OffsetDateTime updatedAt) {
        this.postId = postId;
        this.likeCount = 0;
        this.commentCount = 0;
        this.viewCount = 0;
        this.updatedAt = updatedAt;
    }

    public Long getPostId() {
        return postId;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
