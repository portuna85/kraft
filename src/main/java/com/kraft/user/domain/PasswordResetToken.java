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
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "password_reset_tokens",
        uniqueConstraints = @UniqueConstraint(name = "UK_PASSWORD_RESET_TOKEN", columnNames = "token"),
        indexes = {
                // V13__password_reset_tokens_expires_at_index.sql. 엔티티에 선언이 없어
                // ddl-auto: update로 만든 기존 DB에는 이 인덱스가 생기지 않았다(개선 보고서 O01).
                @Index(name = "IX_PASSWORD_RESET_TOKENS_EXPIRES_AT", columnList = "expires_at"),
        })
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public PasswordResetToken(String token, User user, LocalDateTime expiresAt) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
