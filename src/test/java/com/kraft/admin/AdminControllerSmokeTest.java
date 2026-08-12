package com.kraft.admin;

import com.kraft.winningnumber.WinningNumberCommandService;
import com.kraft.winningnumber.WinningNumberRepository;
import com.kraft.winningnumber.WinningNumberUpsertRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("관리자 컨트롤러 스모크 테스트")
class AdminControllerSmokeTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    WinningNumberCommandService commandService;

    @Autowired
    WinningNumberRepository winningNumberRepository;

    @BeforeEach
    void setUp() {
        winningNumberRepository.deleteAll();
        commandService.upsert(new WinningNumberUpsertRequest(
                1, LocalDate.of(2024, 1, 6),
                List.of(1, 2, 3, 4, 5, 6), 7,
                1_000_000_000L, null, null, null, null
        ));
    }

    @AfterEach
    void tearDown() {
        winningNumberRepository.deleteAll();
    }

    @Test
    @DisplayName("대시보드 조회 시 200 응답을 반환한다")
    void dashboard_returns200() throws Exception {
        mockMvc.perform(get("/admin/dashboard").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회차 관리 페이지 조회 시 200 응답을 반환한다")
    void rounds_returns200() throws Exception {
        mockMvc.perform(get("/admin/rounds").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("M-11: 음수 page로 회차 목록을 요청해도 500 대신 200으로 클램프되어 응답한다")
    void rounds_negativePage_clampsInsteadOf500() throws Exception {
        mockMvc.perform(get("/admin/rounds")
                        .param("page", "-5")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("M-11: 0 이하 size로 회차 목록을 요청해도 500 대신 200으로 클램프되어 응답한다")
    void rounds_nonPositiveSize_clampsInsteadOf500() throws Exception {
        mockMvc.perform(get("/admin/rounds")
                        .param("size", "0")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("M-11: 음수 page로 감사 로그를 요청해도 500 대신 200으로 클램프되어 응답한다")
    void audit_negativePage_clampsInsteadOf500() throws Exception {
        mockMvc.perform(get("/admin/audit")
                        .param("page", "-1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("위조 요청 방지 토큰 없이 로그인 시도 시 만료 페이지로 리다이렉트된다")
    void loginPost_withoutCsrf_redirectsToExpiredLoginPage() throws Exception {
        mockMvc.perform(post("/admin/login")
                        .param("username", "admin")
                        .param("password", "unused"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?expired"));
    }

    // FE-094: 회차를 비운 채 "특정 회차 수집"을 눌러도 최신 수집이 실행되던 문제.
    // 두 작업이 엔드포인트로 분리되었고 회차는 필수다.
    @Test
    @DisplayName("특정 회차 수집은 회차 없이 요청하면 실패 응답을 반환한다")
    void collectRound_withoutRound_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/admin/rounds/collect-round")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("특정 회차 수집은 0 이하 회차를 거부하고 오류와 함께 되돌아간다")
    void collectRound_withNonPositiveRound_redirectsWithError() throws Exception {
        mockMvc.perform(post("/admin/rounds/collect-round")
                        .param("round", "0")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/rounds"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @DisplayName("회차 수집 화면의 회차 입력에는 required 속성이 있고 두 작업이 다른 경로로 제출된다")
    void roundsPage_separatesCollectCommands() throws Exception {
        String html = mockMvc.perform(get("/admin/rounds").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html).contains("/admin/rounds/collect-latest");
        org.assertj.core.api.Assertions.assertThat(html).contains("/admin/rounds/collect-round");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("action=\"/admin/rounds/collect\"");
        org.assertj.core.api.Assertions.assertThat(html).contains("required");
    }
}
