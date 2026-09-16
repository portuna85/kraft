package com.kraft.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    /**
     * 재발급할 때 이 회원의 옛 링크를 무효로 만든다. 파생 삭제 대신 한 문장으로 지운다
     * (개선 보고서 "파생 delete 메서드의 엔티티별 삭제").
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /** 만료 시각이 지난 토큰을 한 번에 지우고, 지운 개수를 돌려준다. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :threshold")
    int deleteByExpiresAtBefore(@Param("threshold") LocalDateTime threshold);
}
