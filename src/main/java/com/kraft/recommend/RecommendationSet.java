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

    @Column(name = "exclusion_policy_version", nullable = false, length = 40)
    private String exclusionPolicyVersion;

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
                              int historyThroughRound, String exclusionPolicyVersion,
                              String lockedNumbers, String excludedNumbers,
                              OffsetDateTime createdAt) {
        this.clientTokenHash = clientTokenHash;
        this.strategy = strategy;
        this.algorithmVersion = algorithmVersion;
        this.historyThroughRound = historyThroughRound;
        this.exclusionPolicyVersion = exclusionPolicyVersion;
        this.lockedNumbers = lockedNumbers;
        this.excludedNumbers = excludedNumbers;
        this.createdAt = createdAt;
    }

    /**
     * KF-01(docs/improvement.md): 로그인 계정 소유로 생성 시점에 바로 만든다 — 이전에는
     * 계정 귀속이 {@link #claimTo}로 사후 이전하는 경로뿐이었다. {@code client_token_hash}는
     * 설정하지 않으므로 {@code chk_recommendation_sets_owner_xor} 제약을 그대로 만족한다.
     */
    public RecommendationSet(Long ownerUserId, String strategy, String algorithmVersion,
                              int historyThroughRound, String exclusionPolicyVersion,
                              String lockedNumbers, String excludedNumbers,
                              OffsetDateTime createdAt) {
        this.ownerUserId = ownerUserId;
        this.strategy = strategy;
        this.algorithmVersion = algorithmVersion;
        this.historyThroughRound = historyThroughRound;
        this.exclusionPolicyVersion = exclusionPolicyVersion;
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

    public String getExclusionPolicyVersion() {
        return exclusionPolicyVersion;
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

    /** 로그인 계정 귀속 — 익명 소유권을 계정 소유권으로 상호 배타 전환한다. */
    void claimTo(Long ownerUserId, OffsetDateTime claimedAt) {
        this.ownerUserId = ownerUserId;
        this.claimedAt = claimedAt;
        this.clientTokenHash = null;
    }
}
