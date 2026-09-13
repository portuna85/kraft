package com.kraft.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    void deleteByUserId(Long userId);

    /** 만료 시각이 지난 토큰을 한 번에 지우고, 지운 개수를 돌려준다. */
    int deleteByExpiresAtBefore(LocalDateTime threshold);
}
