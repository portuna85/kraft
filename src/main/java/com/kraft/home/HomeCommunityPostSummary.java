package com.kraft.home;

import java.time.OffsetDateTime;

/** 홈 화면용 커뮤니티 글 경량 요약 — 본문과 개인화 필드는 제외한다. */
public record HomeCommunityPostSummary(
        Long id,
        String title,
        String authorNameSnapshot,
        OffsetDateTime createdAt
) {
}
