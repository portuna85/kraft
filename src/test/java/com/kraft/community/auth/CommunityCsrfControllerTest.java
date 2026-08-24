package com.kraft.community.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.Application;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * BE-CSRF-01(docs/improvement.md): CommunityCsrfCookieTest와 같은 이유로
 * SecurityMockMvcRequestPostProcessors.csrf() 헬퍼를 쓰지 않는다 — 이 헬퍼는 토큰을
 * 요청에 직접 주입해 실제 "GET → 지연 로딩된 CookieCsrfTokenRepository → CsrfCookieFilter
 * 부수효과로 쿠키 발급" 경로를 우회하므로, 그 경로 자체가 깨져도 못 잡는다.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("익명 CSRF 부트스트랩 endpoint 검증")
class CommunityCsrfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("인증 없이 204를 반환한다(permitAll)")
    void bootstrap_anonymous_returnsNoContent() throws Exception {
        mockMvc.perform(get("/api/v1/community/csrf"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("신원 조회 없이도 XSRF-TOKEN 쿠키를 발급한다")
    void bootstrap_issuesXsrfCookieWithoutIdentityLookup() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/community/csrf"))
                .andReturn()
                .getResponse();

        Cookie xsrfCookie = response.getCookie("XSRF-TOKEN");
        assertThat(xsrfCookie).isNotNull();
        assertThat(xsrfCookie.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("HttpSession(JSESSIONID)을 만들지 않는다 — 진짜 가벼워야 한다")
    void bootstrap_doesNotCreateHttpSession() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/community/csrf"))
                .andReturn()
                .getResponse();

        assertThat(response.getCookie("JSESSIONID")).isNull();
    }

    @Test
    @DisplayName("공용 캐시에 담기지 않는다")
    void bootstrap_isNeverCached() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/community/csrf"))
                .andReturn()
                .getResponse();

        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }
}
