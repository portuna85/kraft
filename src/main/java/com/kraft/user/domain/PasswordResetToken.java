package com.kraft.user.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 비밀번호 재설정 링크에 실리는 1회용 토큰.
 * <p>
 * {@link EmailVerificationToken}과 모양이 같지만 표를 나눈다 — 수명이 30분 대 24시간으로 다르고
 * (비밀번호를 바꿀 수 있는 링크가 하루씩 살아 있을 이유가 없다), 재발송이 한쪽 토큰을 지우는
 * 일이 다른 쪽에 영향을 주면 안 되기 때문이다.
 * <p>
 * 이 토큰은 <b>쓰는 즉시 지운다</b>. 메일함에 남은 링크를 두 번째로 눌러도 아무 일이 없어야 한다.
 * <p>
 * 평문이 아니라 {@link EmailHasher#sha512Hex}로 구한 해시만 저장한다(개선 보고서 SEC-04) —
 * {@link EmailVerificationToken}과 같은 이유다. 이 토큰은 특히 그 자체로 "새 비밀번호 설정"
 * 권한이라 평문 유출의 위험이 더 크다.
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "password_reset_tokens",
        uniqueConstraints = @UniqueConstraint(name = "UK_PASSWORD_RESET_TOKEN_HASH", columnNames = "token_hash"),
        indexes = {
                // V13__password_reset_tokens_expires_at_index.sql. 엔티티에 선언이 없어
                // ddl-auto: update로 만든 기존 DB에는 이 인덱스가 생기지 않았다(개선 보고서 O01).
                @Index(name = "IX_PASSWORD_RESET_TOKENS_EXPIRES_AT", columnList = "expires_at"),
                // V8__password_reset.sql. 엔티티에 선언이 없어 ddl-auto: update로 만든 기존
                // DB에는 이 인덱스가 생기지 않았다(개선 보고서 O01). deleteByUserId(재발급 시
                // 옛 링크 무효화)가 이 FK로 지운다.
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
