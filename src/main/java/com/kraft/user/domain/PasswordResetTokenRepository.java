package com.kraft.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    /** 재발급할 때 이 회원의 옛 링크를 무효로 만든다. */
    void deleteByUserId(Long userId);

    /** 만료 시각이 지난 토큰을 한 번에 지우고, 지운 개수를 돌려준다. */
    int deleteByExpiresAtBefore(LocalDateTime threshold);
}
