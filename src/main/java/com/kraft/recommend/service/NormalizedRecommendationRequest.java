package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationStrategy;

import java.util.Set;

/**
 * 검증·정규화를 마친 요청. {@code RecommendationRequestValidator}만 생성할 수 있다 — 검증을
 * 거치지 않은 값이 서비스 하위 계층에 흘러들지 않게 한다.
 */
public record NormalizedRecommendationRequest(
        int count,
        RecommendationStrategy strategy,
        Set<Integer> lockedNumbers,
        Set<Integer> excludedNumbers
) {
}
