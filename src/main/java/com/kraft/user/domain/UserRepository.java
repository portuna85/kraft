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

    /** 관리자 화면의 "정지 중인 회원". 곧 풀리는 순서로 본다. */
    Page<User> findBySuspendedUntilAfterOrderBySuspendedUntilAsc(LocalDateTime now, Pageable pageable);
}
