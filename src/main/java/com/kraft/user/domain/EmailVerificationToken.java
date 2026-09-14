package com.kraft.user.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원가입 시 발급되는 이메일 인증 토큰. {@code User}가 GUEST에서 USER로 승격되기 전까지
 * 유효하며, 사용(인증 완료) 또는 만료 시 삭제되는 일회용 토큰이다.
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

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
    public EmailVerificationToken(String token, User user, LocalDateTime expiresAt) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
