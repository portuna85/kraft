package com.kraft.community.block;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "community_user_blocks")
public class CommunityUserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_user_id", nullable = false)
    private Long blockerUserId;

    @Column(name = "blocked_user_id", nullable = false)
    private Long blockedUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CommunityUserBlock() {
    }

    public CommunityUserBlock(Long blockerUserId, Long blockedUserId, OffsetDateTime createdAt) {
        this.blockerUserId = blockerUserId;
        this.blockedUserId = blockedUserId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getBlockerUserId() {
        return blockerUserId;
    }

    public Long getBlockedUserId() {
        return blockedUserId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
