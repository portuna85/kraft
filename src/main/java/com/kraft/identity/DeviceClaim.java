package com.kraft.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 기기 토큰이 어느 계정에 귀속됐는지 판정하는 이력. 귀속되는 saved_numbers/recommendation_sets
 * 행은 client_token_hash를 null로 비우므로, 데이터 행만으로는 "이미 귀속됨"을 판정할 수
 * 없다 — 이 테이블이 그 판정의 단일 진실 공급원이다.
 */
@Entity
@Table(name = "device_claims")
public class DeviceClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_token_hash", nullable = false, length = 64)
    private String deviceTokenHash;

    @Column(name = "claimed_by_user_id", nullable = false)
    private Long claimedByUserId;

    @Column(name = "claimed_at", nullable = false)
    private OffsetDateTime claimedAt;

    protected DeviceClaim() {
    }

    public DeviceClaim(String deviceTokenHash, Long claimedByUserId, OffsetDateTime claimedAt) {
        this.deviceTokenHash = deviceTokenHash;
        this.claimedByUserId = claimedByUserId;
        this.claimedAt = claimedAt;
    }

    public Long getId() {
        return id;
    }

    public String getDeviceTokenHash() {
        return deviceTokenHash;
    }

    public Long getClaimedByUserId() {
        return claimedByUserId;
    }

    public OffsetDateTime getClaimedAt() {
        return claimedAt;
    }
}
