package com.kraft.user.web;

import com.kraft.config.security.KraftUserDetails;
import com.kraft.config.security.SecurityConfig;
import com.kraft.config.security.UserDetailsServiceImpl;
import com.kraft.user.service.EmailVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(UserPageController.class)
@Import(SecurityConfig.class)
class UserPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

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
    @DisplayName("GET /forgot-password 는 로그인 없이도 비밀번호 찾기 화면을 보여준다")
    void forgotPassword_isAccessibleWithoutAuthentication() throws Exception {
        // 비밀번호를 잊은 사람은 로그인할 수 없다. 이 화면이 인증을 요구하면 기능 자체가 닫힌다.
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/forgot-password"));
    }

    @Test
    @DisplayName("GET /users/password-reset 은 토큰을 검사하지 않고 화면에 그대로 넘긴다")
    void passwordReset_passesTokenToViewWithoutConsumingIt() throws Exception {
        // 화면을 여는 것만으로 토큰이 소모되면 메일 미리보기·링크 검사기가 대신 눌러 버린다.
        // 판정은 새 비밀번호와 함께 오는 저장 요청에서 한 번만 한다.
        mockMvc.perform(get("/users/password-reset").param("token", "some-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/password-reset"))
                .andExpect(model().attribute("resetToken", "some-token"));
    }

    @Test
    @DisplayName("GET /users/verify 는 토큰을 소비하지 않고 확인 화면만 보여준다(A-FE-04)")
    void verifyEmailConfirm_doesNotConsumeToken() throws Exception {
        mockMvc.perform(get("/users/verify").param("token", "some-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/verify-confirm"))
                .andExpect(model().attribute("token", "some-token"));

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    @DisplayName("POST /users/verify 는 토큰이 유효하면 인증하고 성공 결과로 리다이렉트한다")
    void verifyEmailSubmit_whenTokenValid_redirectsWithSuccess() throws Exception {
        given(emailVerificationService.verify("valid-token")).willReturn(1L);

        mockMvc.perform(post("/users/verify").param("token", "valid-token").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/verify/result"))
                .andExpect(flash().attribute("success", true));
    }

    @Test
    @DisplayName("POST /users/verify 는 토큰이 유효하지 않으면 실패 메시지와 함께 리다이렉트한다")
    void verifyEmailSubmit_whenTokenInvalid_redirectsWithFailureMessage() throws Exception {
        willThrow(new IllegalArgumentException("유효하지 않은 인증 링크입니다."))
                .given(emailVerificationService).verify("invalid-token");

        mockMvc.perform(post("/users/verify").param("token", "invalid-token").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/verify/result"))
                .andExpect(flash().attribute("success", false))
                .andExpect(flash().attribute("message", "유효하지 않은 인증 링크입니다."));
    }

    /** A-BE-08: 지금 요청의 세션이 방금 승격된 바로 그 계정이면 권한을 즉시 갱신한다. */
    @Test
    @DisplayName("POST /users/verify 는 같은 계정으로 로그인한 세션의 권한을 즉시 갱신한다")
    void verifyEmailSubmit_whenSameAccountLoggedIn_refreshesSessionAuthorities() throws Exception {
        given(emailVerificationService.verify("valid-token")).willReturn(1L);
        KraftUserDetails refreshed = new KraftUserDetails(1L, "encoded", "닉네임",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        given(userDetailsService.loadUserById(1L)).willReturn(refreshed);

        KraftUserDetails guestPrincipal = new KraftUserDetails(1L, "encoded", "닉네임",
                List.of(new SimpleGrantedAuthority("ROLE_GUEST")));

        mockMvc.perform(post("/users/verify").param("token", "valid-token").with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(guestPrincipal)))
                .andExpect(status().is3xxRedirection());

        verify(userDetailsService).loadUserById(1L);
    }

}
