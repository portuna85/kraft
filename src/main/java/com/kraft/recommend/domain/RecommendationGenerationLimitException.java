package com.kraft.recommend.domain;

/**
 * 수학적으로는 가능하지만 반복 상한 안에 요청 개수를 채우지 못했을 때 던진다(503, {@code RECOMMENDATION_GENERATION_LIMIT_REACHED}). 수학적으로 불가능한 경우는
 * {@link RecommendationValidationException}({@code INSUFFICIENT_UNIQUE_COMBINATIONS})과 구분한다. 두 경우 모두 부분 성공을 반환하지 않는다.
 */
public class RecommendationGenerationLimitException extends RuntimeException {

    public static final String CODE = "RECOMMENDATION_GENERATION_LIMIT_REACHED";

    public RecommendationGenerationLimitException(String message) {
        super(message);
    }
}
