package com.kraft.web.api;

import com.kraft.config.security.SecurityConfig;
import com.kraft.service.user.EmailVerificationService;
import com.kraft.service.user.UserService;
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

    @Test
    @DisplayName("회원가입은 인증 없이(CSRF 토큰만 있으면) 가능하다")
    void 회원가입은_인증없이_가능하다() throws Exception {
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
    void 회원가입은_CSRF_토큰이_없으면_403() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"tester@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("이름이 50자를 초과하면 400이고 서비스는 호출되지 않는다")
    void 이름이_50자를_초과하면_400() throws Exception {
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
    void 이메일_형식이_잘못되면_400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"not-an-email\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).signUp(any(), any(), any());
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 400")
    void 비밀번호가_8자_미만이면_400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"tester@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호에 대문자·소문자·특수문자가 모두 포함되지 않으면 400")
    void 비밀번호가_복잡도_요건을_충족하지_않으면_400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"tester@example.com\",\"password\":\"alllowercase123\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).signUp(any(), any(), any());
    }

    @Test
    @DisplayName("이메일이 중복이면 400 ProblemDetail을 반환한다")
    void 이메일_중복이면_400() throws Exception {
        given(userService.signUp(any(), any(), any()))
                .willThrow(new IllegalArgumentException("이미 가입된 이메일입니다. email=dup@example.com"));

        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\",\"email\":\"dup@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("이미 가입된 이메일입니다. email=dup@example.com"));
    }

    @Test
    @DisplayName("이름이 중복이면 400 ProblemDetail을 반환한다")
    void 이름_중복이면_400() throws Exception {
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
    void 비밀번호변경은_미인증이면_로그인으로_리다이렉트된다() throws Exception {
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
    void 비밀번호변경은_인증되고_유효하면_204를_반환한다() throws Exception {
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
    void 비밀번호변경은_현재비밀번호_틀리면_400() throws Exception {
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
    void 비밀번호변경은_새비밀번호가_짧으면_400() throws Exception {
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
    void 비밀번호변경은_새비밀번호가_복잡도_요건을_충족하지_않으면_400() throws Exception {
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
    void 인증메일재발송은_미인증이면_로그인으로_리다이렉트된다() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/verify-email/resend")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    @DisplayName("POST /api/v1/users/me/verify-email/resend 는 인증+CSRF면 204를 반환한다")
    void 인증메일재발송은_인증되고_CSRF가_있으면_204를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/verify-email/resend")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(emailVerificationService).resend("tester@example.com");
    }

    @Test
    @DisplayName("POST /api/v1/users/me/verify-email/resend 는 이미 인증된 계정이면 400 ProblemDetail을 반환한다")
    void 인증메일재발송은_이미_인증된_계정이면_400() throws Exception {
        willThrow(new IllegalArgumentException("이미 인증된 계정입니다."))
                .given(emailVerificationService).resend("tester@example.com");

        mockMvc.perform(post("/api/v1/users/me/verify-email/resend")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("이미 인증된 계정입니다."));
    }
}
