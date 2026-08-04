package com.kraft.recommend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// M-17: strategy를 String에서 enum으로 승격하면서도 기존 API/DB 계약(소문자 문자열)이
// 그대로 유지되는지 확인한다 — enum 이름(RANDOM 등)이 그대로 직렬화되면 기존 클라이언트가
// 깨진다.
@DisplayName("RecommendationStrategy 직렬화 계약 테스트")
class RecommendationStrategyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("각 전략은 기존 소문자 문자열 그대로 직렬화된다")
    void wireValue_matchesLegacyLowercaseContract() throws Exception {
        assertThat(objectMapper.writeValueAsString(RecommendationStrategy.RANDOM)).isEqualTo("\"random\"");
        assertThat(objectMapper.writeValueAsString(RecommendationStrategy.BALANCED)).isEqualTo("\"balanced\"");
        assertThat(objectMapper.writeValueAsString(RecommendationStrategy.REDUCE_SHARED_WINNER_RISK))
                .isEqualTo("\"reduce_shared_winner_risk\"");
    }

    @Test
    @DisplayName("fromWire는 저장된 소문자 문자열을 정확히 되돌린다")
    void fromWire_roundTripsStoredValues() {
        assertThat(RecommendationStrategy.fromWire("random")).isEqualTo(RecommendationStrategy.RANDOM);
        assertThat(RecommendationStrategy.fromWire("balanced")).isEqualTo(RecommendationStrategy.BALANCED);
        assertThat(RecommendationStrategy.fromWire("reduce_shared_winner_risk"))
                .isEqualTo(RecommendationStrategy.REDUCE_SHARED_WINNER_RISK);
    }
}
