package com.kraft.user.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByName(String name);

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
}
