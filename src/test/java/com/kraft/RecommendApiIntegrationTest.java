package com.kraft;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("번호 추천 통합 테스트")
class RecommendApiIntegrationTest extends BaseApiIntegrationTest {

    @Test
    @DisplayName("번호 추천 엔드포인트가 요청된 개수만큼 번호를 반환하는지 확인")
    void recommendEndpointReturnsRequestedCount() throws Exception {
        mockMvc.perform(post("/api/v1/numbers/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "count": 2,
                                  "excludedNumbers": [1, 2, 3]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations", hasSize(2)))
                .andExpect(jsonPath("$.recommendations[0]", hasSize(6)));
    }

    @Test
    @DisplayName("번호 추천 시 최대 10세트까지 허용하는지 확인")
    void recommendAllowsUpToTenSets() throws Exception {
        mockMvc.perform(post("/api/v1/numbers/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"count":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations", hasSize(10)));

        mockMvc.perform(post("/api/v1/numbers/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"count":11}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("응답에 전략·이력·역대 1등 조합 제외 정책 메타데이터가 포함된다")
    void recommendResponseIncludesAlgorithmMetadata() throws Exception {
        mockMvc.perform(post("/api/v1/numbers/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"count":1,"reduceSharedWinnerRisk":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy", is("random")))
                .andExpect(jsonPath("$.algorithmVersion", is("uniform-random-v1")))
                .andExpect(jsonPath("$.historyThroughRound", is(2)))
                .andExpect(jsonPath("$.historicalExclusionApplied", is(true)))
                .andExpect(jsonPath("$.exclusionPolicyVersion", is("historical-first-prize-v1")));

        mockMvc.perform(post("/api/v1/numbers/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"count":1,"reduceSharedWinnerRisk":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy", is("reduce_shared_winner_risk")))
                .andExpect(jsonPath("$.algorithmVersion", is("heuristic-v1")));
    }

    @Test
    @DisplayName("구 필드명 maximizePrize를 보내도 reduceSharedWinnerRisk와 동일하게 동작한다(전환기 호환)")
    void recommendAcceptsLegacyMaximizePrizeFieldName() throws Exception {
        mockMvc.perform(post("/api/v1/numbers/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"count":1,"maximizePrize":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy", is("reduce_shared_winner_risk")));
    }
}
