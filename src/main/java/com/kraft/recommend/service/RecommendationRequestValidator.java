package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationValidationException;
import com.kraft.recommend.dto.RecommendRequestDto;
import org.springframework.stereotype.Component;

/**
 * count를 검증하고 기본값을 적용한다. {@code @Valid}는 컨트롤러 경유 요청에서만 동작하므로,
 * 이 검증기는 서비스 경계에서 항상 다시 확인해 직접 호출 경로에도 같은 불변식을 강제한다.
 */
@Component
public class RecommendationRequestValidator {

    private static final int DEFAULT_COUNT = 1;
    private static final int MAX_COUNT = 10;

    /** 검증을 통과한 추천 개수를 돌려준다. 생략하면 1개다. */
    public int validate(RecommendRequestDto request) {
        Integer count = request == null ? null : request.count();
        if (count == null) {
            return DEFAULT_COUNT;
        }
        if (count < 1 || count > MAX_COUNT) {
            throw new RecommendationValidationException(
                    "INVALID_RECOMMENDATION_REQUEST", "count는 1~10 사이여야 합니다.");
        }
        return count;
    }
}
