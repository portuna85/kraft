package com.kraft.user.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원가입 시 발급되는 이메일 인증 토큰. {@code User}가 GUEST에서 USER로 승격되기 전까지
 * 유효하며, 사용(인증 완료) 또는 만료 시 삭제되는 일회용 토큰이다.
 * <p>
 * 평문이 아니라 {@link EmailHasher#sha512Hex}로 구한 해시만 저장한다(개선 보고서 SEC-04) —
 * 토큰(UUID) 자체가 "인증 완료" 권한이라, 평문으로 DB에 남으면 백업 유출이나 DB 접근 권한만
 * 있어도 그 링크를 그대로 쓸 수 있었다. 저속 해시(bcrypt 등)를 쓰지 않는 이유는 UUID가 이미
 * 122비트 무작위성을 가져 사전 대입 공격 대상이 아니기 때문이다 — 사용자가 고르는 비밀번호와는
 * 성격이 다르다.
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "email_verification_tokens", indexes = {
        // V6__image_quota_and_search_indexes.sql. 엔티티에 선언이 없어 ddl-auto: update로
        // 만든 기존 DB에는 이 인덱스가 생기지 않았다(개선 보고서 O01).
        @Index(name = "IX_EVT_EXPIRES_AT", columnList = "expires_at"),
})
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public EmailVerificationToken(String tokenHash, User user, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
