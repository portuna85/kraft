package com.kraft.user.session;

/** 값 목록은 Hibernate가 만드는 것과 같은 알파벳 순이다(V16 마이그레이션의 네이티브 ENUM 참고). */
public enum SessionRevocationTaskStatus {
    DONE,
    FAILED,
    PENDING,
    PROCESSING,
}
