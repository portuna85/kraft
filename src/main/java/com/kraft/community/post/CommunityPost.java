package com.kraft.community.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

@Entity
@Table(name = "community_posts")
public class CommunityPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "author_name_snapshot", nullable = false, length = 100)
    private String authorNameSnapshot;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "category", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private PostCategory category;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PostStatus status;

    @Column(name = "recommendation_set_id")
    private Long recommendationSetId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CommunityPost() {
    }

    public CommunityPost(Long ownerId, String authorNameSnapshot, String title, String content,
                          PostCategory category, Long recommendationSetId,
                          OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.ownerId = ownerId;
        this.authorNameSnapshot = authorNameSnapshot;
        this.title = title;
        this.content = content;
        this.category = category;
        this.status = PostStatus.PUBLISHED;
        this.recommendationSetId = recommendationSetId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getAuthorNameSnapshot() {
        return authorNameSnapshot;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public PostCategory getCategory() {
        return category;
    }

    public PostStatus getStatus() {
        return status;
    }

    public Long getRecommendationSetId() {
        return recommendationSetId;
    }

    public long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    void update(String title, String content, OffsetDateTime updatedAt) {
        this.title = title;
        this.content = content;
        this.updatedAt = updatedAt;
    }

    /** 일반 사용자의 삭제 — 본문은 보존하고 공개 노출만 끈다. */
    void hideByAuthor(OffsetDateTime updatedAt) {
        this.status = PostStatus.HIDDEN_BY_AUTHOR;
        this.updatedAt = updatedAt;
    }

    public void eraseForAccountDeletion(OffsetDateTime updatedAt) {
        this.ownerId = null;
        this.authorNameSnapshot = "삭제된 사용자";
        this.title = "삭제된 게시글입니다.";
        this.content = "삭제된 게시글입니다.";
        this.status = PostStatus.DELETED;
        this.recommendationSetId = null;
        this.updatedAt = updatedAt;
    }
}
