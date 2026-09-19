package com.kraft.recommend.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code app.recommend.enabled=false}면 {@link RecommendationPageController}·
 * {@link RecommendationApiController} 빈이 등록되지 않아 두 경로 모두 404가 됨을 확인한다
 * (운영 준비 — 기능 스위치가 화면·API에 일관되게 적용되는지).
 */
@SpringBootTest(properties = "app.recommend.enabled=false")
@AutoConfigureMockMvc
class RecommendationFeatureFlagTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("기능이 꺼지면 GET /recommend 는 404")
    void pageDisabled_returns404() throws Exception {
        mockMvc.perform(get("/recommend")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("기능이 꺼지면 POST /api/v1/numbers/recommend 는 404")
    void apiDisabled_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/numbers/recommend").with(csrf())).andExpect(status().isNotFound());
    }
}
