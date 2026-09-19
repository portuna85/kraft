package com.kraft.recommend.dto;

import java.util.List;

/**
 * random/reduce_shared_winner_risk 전략은 원본 의미를 유지하기 위해 {@code score=null},
 * {@code explanationCodes=[]}로 반환한다(02문서 4절).
 */
public record RecommendationItemDto(
        int position,
        List<Integer> numbers,
        Integer score,
        List<String> explanationCodes
) {
}
