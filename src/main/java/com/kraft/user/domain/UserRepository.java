package com.kraft.user.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailHmac(String emailHmac);

    boolean existsByEmailHmac(String emailHmac);

    boolean existsByName(String name);

    /** 로그인 실패 횟수를 DB에서 원자적으로 올린다(읽어 고쳐 쓰면 동시 실패가 {@code @Version} 충돌이나 증가분 유실을 낸다). 벌크 UPDATE는 버전을 건드리지 않아 다른 변경과도 충돌하지 않는다. */
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :id")
    int incrementFailedLogins(@Param("id") Long id);

    /** {@link #incrementFailedLogins}와 같은 트랜잭션에서 부른다 — UPDATE가 잡은 행 잠금 덕에 방금 올린 값이 보인다. */
    @Query("SELECT u.failedLoginAttempts FROM User u WHERE u.id = :id")
    int findFailedLoginAttempts(@Param("id") Long id);

    /** 더 늦은 잠금 시각만 반영한다 — 동시 실패 중 짧은 잠금이 긴 잠금을 덮지 않게 한다. */
    @Modifying
    @Query("UPDATE User u SET u.lockedUntil = :until "
            + "WHERE u.id = :id AND (u.lockedUntil IS NULL OR u.lockedUntil < :until)")
    int extendLock(@Param("id") Long id, @Param("until") LocalDateTime until);

    /** 로그인 성공 시 누적·잠금을 지운다. 이미 깨끗하면 쓰기 자체를 하지 않는다. */
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = NULL "
            + "WHERE u.id = :id AND (u.failedLoginAttempts <> 0 OR u.lockedUntil IS NOT NULL)")
    int resetFailedLogins(@Param("id") Long id);

    /** 인증 메일·비밀번호 재설정의 재발급 쿨다운 검사를 계정 단위로 직렬화한다(동시 요청이 같은 "마지막 발송 시각"을 읽고 둘 다 통과하는 경쟁 방지). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * 가입 직후 인증 메일 대기열 등록이 한 번도 성공하지 못한 GUEST 계정을 찾는다(토큰 저장·커밋 실패의 최후 수단). 가입
     * 트랜잭션이 진행 중인 계정은 제외하려 유예시간을 둔다. 탈퇴한 GUEST도 role이 그대로이고 토큰·outbox가 지워져 이 조건에
     * 걸리므로 {@code withdrawnAt IS NULL}로 제외한다(아니면 placeholder 이메일로 발송을 시도한다).
     */
    @Query("SELECT u FROM User u WHERE u.role = com.kraft.user.domain.Role.GUEST "
            + "AND u.createdAt < :threshold "
            + "AND u.withdrawnAt IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM EmailVerificationToken t WHERE t.user = u) "
            + "AND NOT EXISTS (SELECT 1 FROM OutboxMail m WHERE m.user = u "
            + "AND m.kind = com.kraft.user.mail.OutboxMailKind.VERIFY_EMAIL)")
    List<User> findGuestsMissingVerificationMail(@Param("threshold") LocalDateTime threshold, Pageable pageable);
}
