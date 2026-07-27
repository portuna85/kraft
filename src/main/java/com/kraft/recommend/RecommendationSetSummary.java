package com.kraft.recommend;

import java.time.OffsetDateTime;
import java.util.List;

/** 저장된 추천 세트 1건의 상세 뷰 — 이력 목록/단건 조회 응답에 사용한다. */
public record RecommendationSetSummary(
        Long id,
        String strategy,
        String algorithmVersion,
        int historyThroughRound,
        List<Integer> lockedNumbers,
        List<Integer> excludedNumbers,
        OffsetDateTime createdAt,
        List<RecommendationItemView> items
) {
}
