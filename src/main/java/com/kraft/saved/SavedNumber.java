package com.kraft.saved;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "saved_numbers")
public class SavedNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_token_hash", length = 64)
    private String clientTokenHash;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "numbers", nullable = false, length = 32)
    private String numbers;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "source", nullable = false, length = 30)
    private String source;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SavedNumber() {
    }

    public SavedNumber(String clientTokenHash, String numbers, String label, String source, OffsetDateTime createdAt) {
        this.clientTokenHash = clientTokenHash;
        this.numbers = numbers;
        this.label = label;
        this.source = source;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getClientTokenHash() {
        return clientTokenHash;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getNumbers() {
        return numbers;
    }

    public String getLabel() {
        return label;
    }

    public String getSource() {
        return source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** 로그인 계정 귀속 — 익명 소유권을 계정 소유권으로 상호 배타 전환한다. */
    void claimTo(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
        this.clientTokenHash = null;
    }
}
