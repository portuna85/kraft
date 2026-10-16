package com.kraft.post.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고정 공지 상한을 DB 전체에서 직렬화하는 잠금 전용 단일 행(id=1, V45__post_pin_lock.sql). 데이터는 없고
 * {@link PostPinLockRepository#lockForUpdate()}로 행 잠금을 잡는 데만 쓴다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_pin_lock")
public class PostPinLock {

    static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    PostPinLock(Integer id) {
        this.id = id;
    }

    public static PostPinLock singleton() {
        return new PostPinLock(SINGLETON_ID);
    }
}
