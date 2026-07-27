package com.kraft.recommend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "recommendation_sets")
public class RecommendationSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Phase 4(로그인 계정 귀속)에서 채워질 컬럼 — 지금은 항상 null.
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "client_token_hash", length = 64)
    private String clientTokenHash;

    @Column(name = "strategy", nullable = false, length = 40)
    private String strategy;

    @Column(name = "algorithm_version", nullable = false, length = 40)
    private String algorithmVersion;

    @Column(name = "history_through_round", nullable = false)
    private int historyThroughRound;

    @Column(name = "locked_numbers", length = 32)
    private String lockedNumbers;

    @Column(name = "excluded_numbers", length = 160)
    private String excludedNumbers;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    protected RecommendationSet() {
    }

    public RecommendationSet(String clientTokenHash, String strategy, String algorithmVersion,
                              int historyThroughRound, String lockedNumbers, String excludedNumbers,
                              OffsetDateTime createdAt) {
        this.clientTokenHash = clientTokenHash;
        this.strategy = strategy;
        this.algorithmVersion = algorithmVersion;
        this.historyThroughRound = historyThroughRound;
        this.lockedNumbers = lockedNumbers;
        this.excludedNumbers = excludedNumbers;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getClientTokenHash() {
        return clientTokenHash;
    }

    public String getStrategy() {
        return strategy;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public int getHistoryThroughRound() {
        return historyThroughRound;
    }

    public String getLockedNumbers() {
        return lockedNumbers;
    }

    public String getExcludedNumbers() {
        return excludedNumbers;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getClaimedAt() {
        return claimedAt;
    }
}
