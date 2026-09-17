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

    /**
     * 조건부 1회 소비(B07). 같은 토큰을 동시에 두 요청이 들고 오면, DB의 DELETE 행 잠금이
     * 둘 중 하나만 성공시킨다 — 나중 트랜잭션은 이미 지워진 행을 찾지 못해 0을 돌려받는다.
     * 이 반환값으로 "실제로 내가 소비했는가"를 확인한 뒤에만 뒤이은 부수효과(비밀번호 변경)를
     * 실행해야 동시 소비가 둘 다 성공한 것처럼 보이지 않는다.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.id = :id AND t.token = :token")
    int deleteByIdAndToken(@Param("id") Long id, @Param("token") String token);
}
