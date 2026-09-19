package com.kraft.recommend.dto;

import java.util.List;

/**
 * {@code setId}/{@code createdAt}/사용자·기기 식별자는 넣지 않는다 — 비저장 응답이다
 * (02문서 4절). {@code items.length}는 요청 count와 항상 일치한다.
 */
public record RecommendResponseDto(
        String strategy,
        String algorithmVersion,
        int historyThroughRound,
        boolean historicalExclusionApplied,
        String exclusionPolicyVersion,
        List<RecommendationItemDto> items
) {
}
