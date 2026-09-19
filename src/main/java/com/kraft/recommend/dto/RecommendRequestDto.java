package com.kraft.recommend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * 요청 본문은 선택이며, 본문 자체가 없거나 빈 객체({@code {}})면 모든 필드가 null인 것과
 * 동일하게 취급한다({@code RecommendationApiController}). 필드 기본값 적용(count=1,
 * strategy=random, 빈 목록)은 {@code RecommendationRequestValidator}가 서비스 경계에서
 * 수행한다 — Bean Validation에만 의존하면 서비스를 직접 호출하는 경로(예: 테스트)에서 검증이
 * 빠지기 때문이다(02문서 4절).
 */
public record RecommendRequestDto(

        @Min(value = 1, message = "count는 1~10 사이여야 합니다.")
        @Max(value = 10, message = "count는 1~10 사이여야 합니다.")
        Integer count,

        String strategy,

        List<Integer> lockedNumbers,

        List<Integer> excludedNumbers
) {
}
