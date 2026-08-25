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

    // DB-MAP-01(docs/improvement.md): 프로덕션 DDL은 content TEXT NOT NULL(V15:9)이지만
    // columnDefinition이 없으면 Hibernate 기본 매핑은 varchar(255)다. prod는 ddl-auto:
    // validate라 실제 DDL을 그대로 쓰지만, H2 create-drop 테스트 스키마는 varchar(255)로
    // 생성돼 255자를 넘는 본문을 어떤 기본 테스트도 통과시킬 수 없었다.
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
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

    // I-23: 분류도 함께 고칠 수 있다 — 이전에는 발행 시점에 영구 고정이었다.
    void update(String title, String content, PostCategory category, OffsetDateTime updatedAt) {
        this.title = title;
        this.content = content;
        this.category = category;
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
