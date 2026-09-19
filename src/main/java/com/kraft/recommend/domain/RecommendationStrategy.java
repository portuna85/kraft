package com.kraft.recommend.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 번호 추천 전략 세 가지. docs/03-number-recommendation-policy.md의 POL-RANDOM/POL-BALANCED/
 * POL-CHOICE를 그대로 따른다. 전략 코드는 API 계약 문자열이며 대소문자·공백을 정규화한 뒤에만
 * 비교한다(NUM-06, 03문서 3절).
 */
public enum RecommendationStrategy {

    RANDOM("random", "uniform-random-v1"),
    BALANCED("balanced", "balanced-v1"),
    REDUCE_SHARED_WINNER_RISK("reduce_shared_winner_risk", "heuristic-v1");

    private final String code;
    private final String algorithmVersion;

    RecommendationStrategy(String code, String algorithmVersion) {
        this.code = code;
        this.algorithmVersion = algorithmVersion;
    }

    public String code() {
        return code;
    }

    public String algorithmVersion() {
        return algorithmVersion;
    }

    /**
     * 생략/null/공백이면 {@link #RANDOM}(03문서 3절). 나머지는 trim 후 {@link Locale#ROOT} 기준
     * 소문자 정규화한 값이 세 코드 중 하나여야 하며, 그렇지 않으면 빈 값을 반환해 호출자가
     * {@code INVALID_RECOMMENDATION_STRATEGY}로 처리하게 한다.
     */
    public static Optional<RecommendationStrategy> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(RANDOM);
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (RecommendationStrategy strategy : values()) {
            if (strategy.code.equals(normalized)) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }
}
