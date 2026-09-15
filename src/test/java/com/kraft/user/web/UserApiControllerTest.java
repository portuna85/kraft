package com.kraft.user.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.user.service.EmailVerificationService;
import com.kraft.user.service.PasswordResetService;
import com.kraft.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link UserApiController} 웹 계층 테스트. {@code /api/v1/users}는 {@link SecurityConfig}에서
 * permitAll이므로 인증 없이도 호출 가능하지만, CSRF 보호는 그대로 적용됨을 함께 검증한다.
 * {@code /api/v1/users/me/password}는 정확한 문자열(`/api/v1/users`)에만 걸리는 permitAll
 * 규칙에 매치되지 않아 `/api/v1/**` authenticated 규칙으로 떨어짐을 검증한다.
 */
@WebMvcTest(UserApiController.class)
@Import(SecurityConfig.class)
class UserApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @Test
    @DisplayName("회원가입은 인증 없이(CSRF 토큰만 있으면) 가능하다")
    void signUp_isAccessibleWithoutAuthentication() throws Exception {
        given(userService.signUp("tester", "tester@example.com", "Password123!")).willReturn(1L);

        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"tester@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(emailVerificationService).sendVerificationEmailSafely("tester@example.com");
    }

    @Test
    @DisplayName("회원가입은 CSRF 토큰이 없으면 403")
    void signUp_withoutCsrfToken_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"tester@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("이름이 50자를 초과하면 400이고 서비스는 호출되지 않는다")
    void signUp_whenNameExceedsMaxLength_returns400BadRequest() throws Exception {
        String tooLongName = "가".repeat(51);

        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + tooLongName + "\",\"email\":\"tester@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("name: 이름은 50자 이하로 입력하세요."));

        verify(userService, never()).signUp(any(), any(), any());
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 400이고 서비스는 호출되지 않는다")
    void signUp_withInvalidEmailFormat_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"not-an-email\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).signUp(any(), any(), any());
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 400")
    void signUp_whenPasswordIsTooShort_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"tester@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호에 대문자·소문자·특수문자가 모두 포함되지 않으면 400")
    void signUp_whenPasswordDoesNotMeetComplexityRequirements_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"tester@example.com\",\"password\":\"alllowercase123\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).signUp(any(), any(), any());
    }

    @Test
    @DisplayName("이메일이 중복이면 400 ProblemDetail을 반환한다")
    void signUp_whenEmailAlreadyExists_returns400BadRequest() throws Exception {
        given(userService.signUp(any(), any(), any()))
                .willThrow(new IllegalArgumentException("이미 가입된 이메일입니다."));

        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"dup@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("이미 가입된 이메일입니다."));
    }

    @Test
    @DisplayName("이름이 중복이면 400 ProblemDetail을 반환한다")
    void signUp_whenNameAlreadyExists_returns400BadRequest() throws Exception {
        given(userService.signUp(any(), any(), any()))
                .willThrow(new IllegalArgumentException("이미 사용중인 이름입니다. name=dupName"));

        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"dupName\",\"email\":\"new@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("이미 사용중인 이름입니다. name=dupName"));
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/password 는 CSRF 토큰이 있어도 미인증이면 로그인 페이지로 리다이렉트된다")
    void changePassword_whenUnauthenticated_redirectsToLoginPage() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old12345\",\"newPassword\":\"New12345!\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/password 는 인증+CSRF+유효한 본문이면 204를 반환한다")
    void changePassword_whenAuthenticatedAndValid_returns204NoContent() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old12345\",\"newPassword\":\"New12345!\"}"))
                .andExpect(status().isNoContent());

        verify(userService).changePassword("tester@example.com", "old12345", "New12345!");
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/password 는 현재 비밀번호가 틀리면 400 ProblemDetail을 반환한다")
    void changePassword_whenCurrentPasswordMismatch_returns400BadRequest() throws Exception {
        // changePassword는 void 메서드라 BDDMockito.given이 아니라 willThrow(...).given(...) 형태로 스텁한다.
        willThrow(new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다."))
                .given(userService).changePassword("tester@example.com", "wrong", "New12345!");

        mockMvc.perform(put("/api/v1/users/me/password")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"New12345!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("현재 비밀번호가 일치하지 않습니다."));
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/password 는 새 비밀번호가 8자 미만이면 400이고 서비스는 호출되지 않는다")
    void changePassword_whenNewPasswordIsTooShort_returns400BadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old12345\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any(), any());
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/password 는 새 비밀번호에 대문자·소문자·특수문자가 모두 포함되지 않으면 400이고 서비스는 호출되지 않는다")
    void changePassword_whenNewPasswordDoesNotMeetComplexityRequirements_returns400BadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old12345\",\"newPassword\":\"alllowercase123\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/users/me/verify-email/resend 는 CSRF 토큰이 있어도 미인증이면 로그인 페이지로 리다이렉트된다")
    void resendVerificationEmail_whenUnauthenticated_redirectsToLoginPage() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/verify-email/resend")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    @DisplayName("POST /api/v1/users/me/verify-email/resend 는 인증+CSRF면 204를 반환한다")
    void resendVerificationEmail_whenAuthenticatedWithCsrf_returns204NoContent() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/verify-email/resend")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(emailVerificationService).resend("tester@example.com");
    }

    @Test
    @DisplayName("POST /api/v1/users/me/verify-email/resend 는 이미 인증된 계정이면 400 ProblemDetail을 반환한다")
    void resendVerificationEmail_whenAlreadyVerified_returns400BadRequest() throws Exception {
        willThrow(new IllegalArgumentException("이미 인증된 계정입니다."))
                .given(emailVerificationService).resend("tester@example.com");

        mockMvc.perform(post("/api/v1/users/me/verify-email/resend")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("이미 인증된 계정입니다."));
    }

    @Test
    @DisplayName("POST /api/v1/users/password-reset 은 로그인 없이(CSRF 토큰만 있으면) 호출할 수 있다")
    void requestPasswordReset_isAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/users/password-reset")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"tester@example.com\"}"))
                .andExpect(status().isNoContent());

        verify(passwordResetService).request("tester@example.com");
    }

    @Test
    @DisplayName("POST /api/v1/users/password-reset 은 가입 여부와 무관하게 204다 — 응답으로 계정 존재를 알 수 없어야 한다")
    void requestPasswordReset_returnsSameResponseRegardlessOfAccount() throws Exception {
        mockMvc.perform(post("/api/v1/users/password-reset")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("POST /api/v1/users/password-reset 은 이메일 형식이 아니면 400이고 서비스는 호출되지 않는다")
    void requestPasswordReset_withMalformedEmail_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users/password-reset")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("POST /api/v1/users/password-reset/confirm 은 토큰과 새 비밀번호로 204를 반환한다")
    void confirmPasswordReset_withValidBody_returns204NoContent() throws Exception {
        mockMvc.perform(post("/api/v1/users/password-reset/confirm")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-1\",\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isNoContent());

        verify(passwordResetService).reset("token-1", "NewPass1!");
    }

    @Test
    @DisplayName("POST /api/v1/users/password-reset/confirm 은 만료·잘못된 링크면 400 ProblemDetail을 반환한다")
    void confirmPasswordReset_withExpiredToken_returns400BadRequest() throws Exception {
        willThrow(new IllegalArgumentException("재설정 링크가 만료되었습니다. 다시 요청해 주세요."))
                .given(passwordResetService).reset("expired", "NewPass1!");

        mockMvc.perform(post("/api/v1/users/password-reset/confirm")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"expired\",\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("재설정 링크가 만료되었습니다. 다시 요청해 주세요."));
    }

    @Test
    @DisplayName("POST /api/v1/users/password-reset/confirm 은 새 비밀번호가 규칙에 맞지 않으면 400이고 서비스는 호출되지 않는다")
    void confirmPasswordReset_withWeakPassword_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users/password-reset/confirm")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-1\",\"newPassword\":\"alllowercase1\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me 는 인증+CSRF+현재 비밀번호면 204를 반환한다")
    void withdraw_whenAuthenticatedWithCsrf_returns204NoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password123!\"}"))
                .andExpect(status().isNoContent());

        verify(userService).withdraw("tester@example.com", "Password123!");
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me 는 미인증이면 로그인 페이지로 리다이렉트되고 서비스는 호출되지 않는다")
    void withdraw_whenNotAuthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password123!\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me 는 CSRF 토큰이 없으면 403")
    void withdraw_withoutCsrfToken_returns403Forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .with(user("tester@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password123!\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me 는 현재 비밀번호가 틀리면 400 ProblemDetail을 반환한다")
    void withdraw_whenPasswordDoesNotMatch_returns400BadRequest() throws Exception {
        willThrow(new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다."))
                .given(userService).withdraw("tester@example.com", "WrongPass1!");

        mockMvc.perform(delete("/api/v1/users/me")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("현재 비밀번호가 일치하지 않습니다."));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me 는 비밀번호가 비어 있으면 400이고 서비스는 호출되지 않는다")
    void withdraw_withBlankPassword_returns400BadRequest() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }
}
