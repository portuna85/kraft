package com.kraft.community.post;

/**
 * 게시글 상태 4종. 일반 사용자의 "삭제"는 {@link #HIDDEN_BY_AUTHOR}로만
 * 전환한다(본문 보존, 참조·감사 관계 유지) — 하드 삭제는 별도 운영 절차 전용이라
 * 이 애플리케이션 상태로는 노출하지 않는다.
 */
public enum PostStatus {
    PUBLISHED,
    HIDDEN_BY_AUTHOR,
    HIDDEN_BY_MODERATOR,
    DELETED
}
