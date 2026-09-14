package com.kraft.user.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.user.service.EmailVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(UserPageController.class)
@Import(SecurityConfig.class)
class UserPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @Test
    @DisplayName("로그인 화면에도 현재 경로가 전달된다")
    void loginIncludesNavigationModel() throws Exception {
        mockMvc.perform(get("/login?redirect=/posts/save"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/login"))
                .andExpect(model().attribute("currentPath", "/login?redirect=/posts/save"));
    }

    @Test
    @DisplayName("GET /signup 은 인증 없이도 회원가입 화면을 보여준다")
    void signup_isAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/signup"));
    }

    @Test
    @DisplayName("GET /users/me/password 는 더 이상 화면이 아니다(비밀번호 변경은 모달로 옮겼다)")
    void changePasswordPage_isGone() throws Exception {
        mockMvc.perform(get("/users/me/password"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /users/verify 는 토큰이 유효하면 success=true로 렌더링한다")
    void verifyEmail_whenTokenValid_rendersSuccess() throws Exception {
        mockMvc.perform(get("/users/verify").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/verify-result"))
                .andExpect(model().attribute("success", true));
    }

    @Test
    @DisplayName("GET /users/verify 는 토큰이 유효하지 않으면 success=false와 메시지를 담아 렌더링한다")
    void verifyEmail_whenTokenInvalid_rendersFailureWithMessage() throws Exception {
        willThrow(new IllegalArgumentException("유효하지 않은 인증 링크입니다."))
                .given(emailVerificationService).verify("invalid-token");

        mockMvc.perform(get("/users/verify").param("token", "invalid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/verify-result"))
                .andExpect(model().attribute("success", false))
                .andExpect(model().attribute("message", "유효하지 않은 인증 링크입니다."));
    }

}
