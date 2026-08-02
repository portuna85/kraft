package com.kraft.community.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

@Entity
@Table(name = "community_comments")
public class CommunityComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "author_name_snapshot", nullable = false, length = 100)
    private String authorNameSnapshot;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CommunityComment() {
    }

    public CommunityComment(Long postId, Long parentId, Long ownerId, String authorNameSnapshot, String content,
                             OffsetDateTime createdAt) {
        this.postId = postId;
        this.parentId = parentId;
        this.ownerId = ownerId;
        this.authorNameSnapshot = authorNameSnapshot;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getParentId() {
        return parentId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getAuthorNameSnapshot() {
        return authorNameSnapshot;
    }

    public String getContent() {
        return content;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    void markDeleted() {
        this.deleted = true;
    }

    public void eraseForAccountDeletion() {
        this.ownerId = null;
        this.authorNameSnapshot = "삭제된 사용자";
        this.content = "삭제된 댓글입니다.";
        this.deleted = true;
    }
}
