package com.kraft.common.web;

import com.kraft.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("보안 헤더 필터 테스트")
class SecurityHeadersFilterTest {

    @Autowired
    private MockMvc mockMvc;

    // I-10: X-Content-Type-Options·X-Frame-Options·Referrer-Policy·Permissions-Policy·HSTS는
    // caddy/Caddyfile이 사이트 전역으로 발급하는 단일 소스로 옮겼다 — 이 필터는 이제 Caddy가
    // 낼 수 없는 CSP만 다룬다(중복 발급을 없애는 게 목적이었으므로 이 필터가 다시 붙이지
    // 않는지도 함께 확인한다).
    @Test
    @DisplayName("일반 API 경로에는 CSP만 이 필터가 붙이고 나머지 보안 헤더는 붙이지 않는다")
    void ordinaryApiPath_getsOnlyCspFromThisFilter() throws Exception {
        mockMvc.perform(get("/api/v1/stats/patterns"))
                .andExpect(header().doesNotExist("X-Content-Type-Options"))
                .andExpect(header().doesNotExist("X-Frame-Options"))
                .andExpect(header().doesNotExist("Referrer-Policy"))
                .andExpect(header().doesNotExist("Permissions-Policy"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    @DisplayName("/admin 경로는 default-src 'none' CSP를 강제하지 않는다")
    void adminPath_doesNotForceDefaultCsp() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(result -> {
                    String csp = result.getResponse().getHeader("Content-Security-Policy");
                    if (csp != null && csp.contains("default-src 'none'")) {
                        throw new AssertionError("admin 경로에 API용 CSP가 적용됨: " + csp);
                    }
                });
    }

    @Test
    @DisplayName("/h2-console 경로는 default-src 'none' CSP를 강제하지 않는다")
    void h2ConsolePath_doesNotForceDefaultCsp() throws Exception {
        mockMvc.perform(get("/h2-console"))
                .andExpect(result -> {
                    String csp = result.getResponse().getHeader("Content-Security-Policy");
                    if (csp != null && csp.contains("default-src 'none'")) {
                        throw new AssertionError("h2-console 경로에 API용 CSP가 적용됨: " + csp);
                    }
                });
    }
}
