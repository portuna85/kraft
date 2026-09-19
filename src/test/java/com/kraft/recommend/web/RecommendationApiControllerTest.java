package com.kraft.recommend.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.recommend.domain.RecommendationHistoryNotReadyException;
import com.kraft.recommend.domain.RecommendationValidationException;
import com.kraft.recommend.dto.RecommendResponseDto;
import com.kraft.recommend.dto.RecommendationItemDto;
import com.kraft.recommend.service.RecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RecommendationApiController} 웹 계층 테스트. {@link SecurityConfig}를 그대로
 * {@code @Import}해 permitAll·CSRF 규칙과 {@code ApiExceptionHandler}의 {@code code} 확장 속성을
 * 함께 검증한다(CommentApiControllerTest와 동일한 방식).
 */
@WebMvcTest(RecommendationApiController.class)
@Import(SecurityConfig.class)
class RecommendationApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @MockitoBean
    private RecommendationRateLimiter rateLimiter;

    @Test
    @DisplayName("POST /api/v1/numbers/recommend 는 CSRF 토큰이 없으면 403")
    void recommend_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/numbers/recommend").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/numbers/recommend 는 CSRF만 있으면 인증 없이도 성공한다(비저장 공개 API)")
    void recommend_withCsrfAndNoAuthentication_returns200() throws Exception {
        given(rateLimiter.tryAcquire(any())).willReturn(true);
        given(recommendationService.recommend(any())).willReturn(new RecommendResponseDto(
                "random", "uniform-random-v1", 100, true, "historical-first-prize-v1",
                List.of(new RecommendationItemDto(1, List.of(1, 2, 3, 4, 5, 6), null, List.of()))));

        mockMvc.perform(post("/api/v1/numbers/recommend").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("random"))
                .andExpect(jsonPath("$.items[0].numbers[0]").value(1));
    }

    @Test
    @DisplayName("서비스가 검증 예외를 던지면 400과 code 확장 속성을 그대로 내려준다")
    void recommend_whenValidationFails_returns400WithCode() throws Exception {
        given(rateLimiter.tryAcquire(any())).willReturn(true);
        given(recommendationService.recommend(any()))
                .willThrow(new RecommendationValidationException("INVALID_RECOMMENDATION_STRATEGY", "지원하지 않는 전략입니다."));

        mockMvc.perform(post("/api/v1/numbers/recommend")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\":\"maximizePrize\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECOMMENDATION_STRATEGY"));
    }

    @Test
    @DisplayName("이력이 준비되지 않으면 503과 RECOMMENDATION_HISTORY_NOT_READY를 반환한다")
    void recommend_whenHistoryNotReady_returns503() throws Exception {
        given(rateLimiter.tryAcquire(any())).willReturn(true);
        given(recommendationService.recommend(any()))
                .willThrow(new RecommendationHistoryNotReadyException("검증된 당첨 이력이 준비되지 않았습니다."));

        mockMvc.perform(post("/api/v1/numbers/recommend").with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_HISTORY_NOT_READY"));
    }

    @Test
    @DisplayName("요청 제한을 초과하면 429와 Retry-After를 반환하고 서비스는 호출되지 않는다")
    void recommend_whenRateLimited_returns429() throws Exception {
        given(rateLimiter.tryAcquire(any())).willReturn(false);

        mockMvc.perform(post("/api/v1/numbers/recommend").with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_RATE_LIMITED"));

        org.mockito.Mockito.verifyNoInteractions(recommendationService);
    }
}
