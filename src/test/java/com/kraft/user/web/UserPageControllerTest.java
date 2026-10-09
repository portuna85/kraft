package com.kraft.user.web;

import com.kraft.shared.exception.BusinessValidationException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
        mockMvc.perform(get("/users/me/password").with(user("member").roles("USER")))
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
    @DisplayName("GET /users/password-reset 은 토큰을 요구하지 않고 화면만 보여준다(토큰은 URL 프래그먼트로 온다)")
    void passwordReset_rendersViewWithoutRequiringTokenParam() throws Exception {
        // 토큰은 브라우저가 서버로 보내지 않는 프래그먼트(#token=...)에 있다 — 이 요청 자체에는
        // 토큰이 실리지 않는다. 화면(Vue)이 location.hash에서 직접 읽는다.
        mockMvc.perform(get("/users/password-reset"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/password-reset"));
    }

    @Test
    @DisplayName("GET /users/verify 는 토큰을 소비하지 않고 확인 화면만 보여준다")
    void verifyEmailConfirm_doesNotConsumeToken() throws Exception {
        mockMvc.perform(get("/users/verify").param("token", "some-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/verify-confirm"))
                .andExpect(model().attribute("token", "some-token"));

        verifyNoInteractions(emailVerificationService);
    }

    /** 새 메일은 토큰을 프래그먼트로 보내므로 서버가 받는 요청에는 token이 없다. */
    @Test
    @DisplayName("GET /users/verify 는 token 쿼리가 없어도(프래그먼트 링크) 확인 화면을 보여준다")
    void verifyEmailConfirm_withoutQueryToken_stillRenders() throws Exception {
        mockMvc.perform(get("/users/verify"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/verify-confirm"))
                .andExpect(model().attribute("token", ""));

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
        willThrow(new BusinessValidationException("유효하지 않은 인증 링크입니다."))
                .given(emailVerificationService).verify("invalid-token");

        mockMvc.perform(post("/users/verify").param("token", "invalid-token").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/verify/result"))
                .andExpect(flash().attribute("success", false))
                .andExpect(flash().attribute("message", "유효하지 않은 인증 링크입니다."));
    }

    /** 지금 요청의 세션이 방금 승격된 바로 그 계정이면 권한을 즉시 갱신한다. */
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

    /** 갱신된 principal이 BCrypt 해시를 들고 세션에 직렬화되지 않는다. */
    @Test
    @DisplayName("POST /users/verify 로 갱신한 세션 principal에는 비밀번호 해시가 남지 않는다")
    void verifyEmailSubmit_whenSessionRefreshed_erasesPasswordHash() throws Exception {
        given(emailVerificationService.verify("valid-token")).willReturn(1L);
        KraftUserDetails refreshed = new KraftUserDetails(1L, "encoded-hash", "닉네임",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        given(userDetailsService.loadUserById(1L)).willReturn(refreshed);

        KraftUserDetails guestPrincipal = new KraftUserDetails(1L, "encoded", "닉네임",
                List.of(new SimpleGrantedAuthority("ROLE_GUEST")));

        var session = mockMvc.perform(post("/users/verify").param("token", "valid-token").with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(guestPrincipal)))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession(false);

        var savedContext = (org.springframework.security.core.context.SecurityContext) session.getAttribute(
                org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(savedContext.getAuthentication().getCredentials()).isNull();
        assertThat(((KraftUserDetails) savedContext.getAuthentication().getPrincipal()).getPassword()).isNull();
    }

}
