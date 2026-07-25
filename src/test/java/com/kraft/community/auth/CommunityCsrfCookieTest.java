package com.kraft.community.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.Application;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * SecurityMockMvcRequestPostProcessors.csrf()는 토큰을 요청에 직접 주입해 실제
 * "GET으로 쿠키 발급 → 그 쿠키 값을 X-XSRF-TOKEN 헤더로 되돌려 보내는" 브라우저 경로를
 * 검증하지 않는다. CookieCsrfTokenRepository는 토큰을 지연 로딩하므로(Spring Security 6+)
 * CsrfCookieFilter(CommunitySecurityConfig)가 없으면 이 왕복이 조용히 실패한다 —
 * 그 회귀를 잡기 위해 csrf() 헬퍼 없이 실제 쿠키 발급 경로를 그대로 재현한다.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("커뮤니티 CSRF 쿠키 발급·왕복 검증")
class CommunityCsrfCookieTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    private CommunityUser owner;

    @BeforeEach
    void setUp() {
        owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "글쓴이", null, OffsetDateTime.now()));
    }

    @Test
    @DisplayName("세션 조회 GET만으로 XSRF-TOKEN 쿠키가 발급된다")
    void sessionCheck_issuesXsrfCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/community/session"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        Cookie xsrfCookie = response.getCookie("XSRF-TOKEN");
        assertThat(xsrfCookie).isNotNull();
        assertThat(xsrfCookie.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("GET으로 발급받은 쿠키 값을 헤더로 되돌려 보내면 로그아웃이 성공한다")
    void logout_succeedsWithCookieIssuedByPriorGet() throws Exception {
        MockHttpServletResponse sessionResponse = mockMvc.perform(get("/api/v1/community/session"))
                .andReturn()
                .getResponse();
        Cookie xsrfCookie = sessionResponse.getCookie("XSRF-TOKEN");
        assertThat(xsrfCookie).isNotNull();

        mockMvc.perform(post("/logout")
                        .with(asUser(owner))
                        .cookie(xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
                .andExpect(status().is3xxRedirection());
    }

    private RequestPostProcessor asUser(CommunityUser user) {
        CommunityPrincipal principal = new CommunityPrincipal(user.getId(), user.getNickname());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(authentication);
    }
}
