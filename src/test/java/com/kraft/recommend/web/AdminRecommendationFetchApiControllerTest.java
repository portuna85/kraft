package com.kraft.recommend.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.recommend.domain.RecommendationFetchAttempt.Trigger;
import com.kraft.recommend.service.RecommendationFetchService;
import com.kraft.recommend.service.RecommendationFetchService.RunResult;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "지금 수집"은 운영 DB를 바꾸는 동작이라 관리자만, CSRF 토큰과 함께만 실행된다. */
@WebMvcTest(AdminRecommendationFetchApiController.class)
@Import(SecurityConfig.class)
class AdminRecommendationFetchApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationFetchService fetchService;

    /** SecurityConfig가 UserDetailsService를 필요로 한다(폼 로그인 구성). */
    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("관리자가 아니면 수집을 시작할 수 없다")
    void fetchNow_whenNotAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/recommendations/fetch").with(csrf())
                        .with(user("tester@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(fetchService);
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 관리자라도 거절한다")
    void fetchNow_withoutCsrf_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/recommendations/fetch")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(fetchService);
    }

    @Test
    @DisplayName("관리자가 요청하면 수동으로 한 번 수집하고 결과를 돌려준다")
    void fetchNow_whenAdmin_runsManualFetchAndReturnsResult() throws Exception {
        given(fetchService.run(Trigger.MANUAL)).willReturn(
                new RunResult(RunResult.Status.DONE, List.of(1244), "1개 회차를 반영했습니다."));

        mockMvc.perform(post("/api/v1/admin/recommendations/fetch").with(csrf())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.fetchedRounds[0]").value(1244))
                .andExpect(jsonPath("$.message").value("1개 회차를 반영했습니다."));

        verify(fetchService).run(Trigger.MANUAL);
    }

    @Test
    @DisplayName("이미 수집이 돌고 있으면 BUSY를 그대로 알린다")
    void fetchNow_whenBusy_reportsBusy() throws Exception {
        given(fetchService.run(Trigger.MANUAL)).willReturn(
                new RunResult(RunResult.Status.BUSY, List.of(), "이미 다른 수집이 진행 중입니다."));

        mockMvc.perform(post("/api/v1/admin/recommendations/fetch").with(csrf())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BUSY"));
    }
}
