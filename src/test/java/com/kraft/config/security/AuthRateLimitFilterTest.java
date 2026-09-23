package com.kraft.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AuthRateLimitFilter} 통합 테스트(개선 보고서 SEC-01). 기본 test 프로파일은 다른
 * MockMvc 테스트가 흔들리지 않도록 이 제한기를 꺼 두므로(application-test.yml), 이 클래스만
 * {@code @TestPropertySource}로 다시 켜고 한도를 작게 잡아 검증한다.
 * <p>
 * {@link AuthRateLimitFilter}는 싱글턴 빈이라 메모리 카운터가 테스트 메서드 사이에 그대로
 * 남는다 — 특히 로그인 IP 제한기는 모든 로그인 테스트가 같은 MockMvc 기본 IP(127.0.0.1)를
 * 공유해 서로의 한도를 갉아먹는다. 메서드마다 컨텍스트를 새로 띄워 제한기를 초기화한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "app.auth.rate-limit.enabled=true",
        "app.auth.rate-limit.login-per-minute=3",
        "app.auth.rate-limit.login-account-per-minute=2",
        "app.auth.rate-limit.signup-per-minute=2",
        "app.auth.rate-limit.password-reset-per-minute=2",
        "app.auth.rate-limit.resend-per-minute=2"
})
class AuthRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("로그인: 같은 IP에서 계정을 바꿔 가며 한도를 넘기면 throttled로 리다이렉트한다")
    void login_exceedingIpLimit_redirectsToThrottled() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/login")
                            .param("username", "no-such-user-" + i + "@example.com")
                            .param("password", "wrong-password")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }

        mockMvc.perform(post("/login")
                        .param("username", "no-such-user-3@example.com")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=throttled"));
    }

    @Test
    @DisplayName("로그인: 같은 계정으로 반복하면 IP 한도보다 먼저 계정 한도에 걸린다")
    void login_exceedingAccountLimit_redirectsToThrottled() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/login")
                            .param("username", "same-account@example.com")
                            .param("password", "wrong-password")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }

        mockMvc.perform(post("/login")
                        .param("username", "same-account@example.com")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=throttled"));
    }

    @Test
    @DisplayName("가입: IP당 한도를 넘긴 요청은 429와 AUTH_RATE_LIMITED 코드를 받는다")
    void signup_exceedingLimit_returns429() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/users")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"tester\",\"email\":\"rate-signup-" + i
                            + "@example.com\",\"password\":\"Password123!\"}"));
        }

        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"rate-signup-2@example.com\","
                                + "\"password\":\"Password123!\"}"))
                .andExpect(status().is(429))
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"));
    }

    @Test
    @DisplayName("비밀번호 재설정 요청: IP당 한도를 넘기면 429를 받는다")
    void passwordReset_exceedingLimit_returns429() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/users/password-reset")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"reset-" + i + "@example.com\"}"));
        }

        mockMvc.perform(post("/api/v1/users/password-reset")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset-2@example.com\"}"))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"));
    }
}
