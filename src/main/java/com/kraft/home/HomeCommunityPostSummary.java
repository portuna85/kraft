package com.kraft.home;

import java.time.OffsetDateTime;

/** 홈 화면용 커뮤니티 글 경량 요약 — 본문은 제외한다(문서 13.1절, 개인화 필드 없음). */
public record HomeCommunityPostSummary(
        Long id,
        String title,
        String authorNameSnapshot,
        OffsetDateTime createdAt
) {
}
