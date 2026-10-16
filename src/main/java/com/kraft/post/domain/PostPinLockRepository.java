package com.kraft.post.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PostPinLockRepository extends JpaRepository<PostPinLock, Integer> {

    /**
     * 단일 잠금 행을 {@code SELECT ... FOR UPDATE}로 잡는다. 호출한 트랜잭션이 끝날 때까지 다른 고정·복구·숨김 해제는 여기서 기다린다.
     * 행은 마이그레이션이 만들지만 스키마를 JPA가 만드는 테스트 DB에는 없을 수 있어, 없으면 호출자가 만든다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM PostPinLock l WHERE l.id = 1")
    Optional<PostPinLock> lockForUpdate();
}
