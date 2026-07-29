package com.kraft.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 보안 체인 순서(Admin=@Order(1), Community=@Order(2), Public=@Order(3))와 matcher
 * 선점 상태를 고정하는 회귀 가드. community 체인이 public 체인보다 먼저 평가되지 않으면
 * "/api/v1/community/**"가 public 체인의 넓은 "/api/**" matcher에 무음으로 선점당한다.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("보안 체인 순서·matcher 선점 회귀 가드")
class SecurityChainOrderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("미인증 admin 보호 경로는 admin 로그인 페이지로 리다이렉트된다")
    void unauthenticatedAdminPath_redirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/login"));
    }

    @Test
    @DisplayName("공개 API 경로는 STATELESS로 처리되어 세션 쿠키가 생성되지 않는다")
    void publicApiPath_isStatelessAndSetsNoSessionCookie() throws Exception {
        mockMvc.perform(get("/api/v1/rounds/latest"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    @DisplayName("미인증 community 보호 경로는 public 체인이 아닌 community 체인에 도달해 401 JSON을 받는다")
    void unauthenticatedCommunityPath_reachesCommunityChainNotPublicChain() throws Exception {
        // /api/v1/community/posts/**는 공개 조회로 permitAll이므로, 아직 인증이 필요한
        // 임의의(향후 확장용) community 경로로 "community 체인 인증 요구" 자체를 검증한다.
        mockMvc.perform(get("/api/v1/community/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                // B-02: 인증 실패도 다른 오류 경로와 동일한 ApiErrorResponse 계약을 지키는지 확인.
                .andExpect(jsonPath("$.code").value("COMMUNITY_LOGIN_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/community/notifications"));
    }

    @Test
    @DisplayName("익명 요청으로 커뮤니티 세션 조회 시 세션 쿠키를 만들지 않고 로그인 안 됨 상태를 반환한다")
    void anonymousSessionCheck_doesNotCreateSessionCookie() throws Exception {
        mockMvc.perform(get("/api/v1/community/session"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }
}
