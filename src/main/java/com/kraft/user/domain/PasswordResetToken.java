package com.kraft.user.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 비밀번호 재설정 링크의 1회용 토큰. {@link EmailVerificationToken}과 모양이 같지만 표를 나눈다 — 수명이 30분 대 24시간으로
 * 다르고, 재발송이 한쪽 토큰을 지우는 일이 다른 쪽에 영향을 주면 안 된다. 쓰는 즉시 지우며, 그 자체가 "새 비밀번호 설정"
 * 권한이라 평문이 아니라 {@link EmailHasher#sha512Hex} 해시만 저장한다.
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "password_reset_tokens",
        uniqueConstraints = @UniqueConstraint(name = "UK_PASSWORD_RESET_TOKEN_HASH", columnNames = "token_hash"),
        indexes = {
                // 인덱스는 Flyway(V8·V13)와 일치해야 한다.
                @Index(name = "IX_PASSWORD_RESET_TOKENS_EXPIRES_AT", columnList = "expires_at"),
                // deleteByUserId(재발급 시 옛 링크 무효화)가 이 FK로 지운다.
                @Index(name = "IX_PASSWORD_RESET_TOKENS_USER", columnList = "user_id"),
        })
public class PasswordResetToken {

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
    public PasswordResetToken(String tokenHash, User user, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
