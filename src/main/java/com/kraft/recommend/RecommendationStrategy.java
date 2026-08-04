package com.kraft.recommend;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 추천 전략. 기존 API 계약(소문자 문자열)과 DB 저장 값을 그대로 유지하기 위해 열거형
 * 이름이 아니라 wireValue를 직렬화 값으로 쓴다.
 */
public enum RecommendationStrategy {
    RANDOM("random"),
    BALANCED("balanced"),
    REDUCE_SHARED_WINNER_RISK("reduce_shared_winner_risk");

    private final String wireValue;

    RecommendationStrategy(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /** DB/내부 로직이 다루는 소문자 문자열(요청 검증을 이미 통과한 값)을 enum으로 되돌린다. */
    static RecommendationStrategy fromWire(String value) {
        for (RecommendationStrategy strategy : values()) {
            if (strategy.wireValue.equals(value)) {
                return strategy;
            }
        }
        throw new IllegalStateException("알 수 없는 추천 전략 값: " + value);
    }
}
