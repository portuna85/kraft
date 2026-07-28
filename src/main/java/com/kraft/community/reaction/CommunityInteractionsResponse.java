package com.kraft.community.reaction;

import java.util.List;
import java.util.Set;

/** 로그인 사용자의 개인 반응 상태 — 공개 피드 응답과 분리해 캐시되지 않는다(문서 11.5, 13.4). */
public record CommunityInteractionsResponse(
        Set<Long> likedPostIds,
        Set<Long> bookmarkedPostIds,
        List<Long> blockedUserIds
) {
}
