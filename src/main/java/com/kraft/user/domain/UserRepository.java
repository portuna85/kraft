package com.kraft.user.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByName(String name);

    /**
     * 이메일 인증·비밀번호 재설정의 재발급 쿨다운 검사를 계정 단위로 직렬화한다(B07). 검사
     * (마지막 발송 시각 조회)와 실행(토큰 재발급·대기열 등록)을 하나의 원자적 구간으로 묶어,
     * 같은 계정에 대한 두 동시 요청이 같은 "마지막 발송 시각"을 동시에 읽고 둘 다 쿨다운을
     * 통과하는 경쟁을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * 관리자 화면의 "정지 중인 회원" 목록 전용 projection. 이메일 암호문 등 화면에 쓰지 않는
     * 컬럼까지 포함한 User 전체를 로딩하지 않는다(개선 보고서 "인덱스·암호화·주석의 유지보수
     * 경계"). getter 이름은 User 엔티티의 필드명과 정확히 일치해야 Spring Data가 인식한다.
     */
    interface SuspendedUserProjection {
        Long getId();

        String getName();

        LocalDateTime getSuspendedUntil();

        String getSuspensionReason();
    }

    /** 관리자 화면의 "정지 중인 회원". 곧 풀리는 순서로 본다. */
    Page<SuspendedUserProjection> findBySuspendedUntilAfterOrderBySuspendedUntilAsc(
            LocalDateTime now, Pageable pageable);

    /**
     * B08의 최후 수단이 쓴다. 가입 직후 인증 메일 대기열 등록이(토큰 저장 실패, 최종 커밋 실패
     * 등으로) 한 번도 성공하지 못한 GUEST 계정을 찾는다. 유예시간을 두는 이유는, 가입 트랜잭션이
     * 아직 진행 중이거나 방금 커밋된 계정까지 대상으로 삼으면 안 되기 때문이다.
     */
    @Query("SELECT u FROM User u WHERE u.role = com.kraft.user.domain.Role.GUEST "
            + "AND u.createdAt < :threshold "
            + "AND NOT EXISTS (SELECT 1 FROM EmailVerificationToken t WHERE t.user = u) "
            + "AND NOT EXISTS (SELECT 1 FROM OutboxMail m WHERE m.user = u "
            + "AND m.kind = com.kraft.user.mail.OutboxMailKind.VERIFY_EMAIL)")
    List<User> findGuestsMissingVerificationMail(@Param("threshold") LocalDateTime threshold, Pageable pageable);
}
