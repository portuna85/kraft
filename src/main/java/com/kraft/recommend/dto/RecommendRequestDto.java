package com.kraft.recommend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 요청 본문은 선택이며, 본문 자체가 없거나 빈 객체({@code {}})면 count가 null인 것과 동일하게
 * 취급한다({@code RecommendationApiController}). 기본값(count=1) 적용은
 * {@code RecommendationRequestValidator}가 서비스 경계에서 수행한다 — Bean Validation에만
 * 의존하면 서비스를 직접 호출하는 경로(예: 테스트)에서 검증이 빠지기 때문이다.
 * <p>
 * 추천 방식·고정/제외 번호는 없다. 역대 1등 조합 제외만 고정 동작이다. 옛 클라이언트가
 * {@code strategy} 같은 필드를 보내도 무시한다.
 */
public record RecommendRequestDto(

        @Min(value = 1, message = "count는 1~10 사이여야 합니다.")
        @Max(value = 10, message = "count는 1~10 사이여야 합니다.")
        Integer count
) {
}
