package com.kraft.user.service;

import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailRepository;
import com.kraft.user.session.SessionRevocationTaskRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커밋 후 세션 폐기가 실패해도 이미 커밋된 비밀번호 변경이 실패 응답으로 보이지 않는지
 * 검증한다(개선 보고서 "커밋 후 실패가 이미 커밋된 변경을 실패 응답으로 보이게 함").
 * <p>
 * {@link SessionRevoker}를 별도로 mock으로 대체해야 하므로 {@link PasswordChangeSessionRevocationTest}와
 * 다른 스프링 컨텍스트를 쓴다 — 그 클래스는 실제 세션 폐기 동작 자체를 검증하므로 대역을 쓸 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeAfterCommitFailureTest {

    private static final String EMAIL = "aftercommit-failure@example.com";
    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private OutboxMailRepository outboxMailRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private SessionRevocationTaskRepository sessionRevocationTaskRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private SessionRevoker sessionRevoker;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        outboxMailRepository.deleteAll();
        tokenRepository.deleteAll();
        sessionRevocationTaskRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .name("tester")
                .email(EMAIL)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .build());
    }

    @Test
    @DisplayName("세션 폐기가 실패해도 이미 커밋된 비밀번호 변경은 성공 응답을 유지한다")
    void changePassword_whenSessionRevocationFails_stillReturnsSuccessAndPersistsChange() throws Exception {
        willThrow(new RuntimeException("세션 저장소 장애")).given(sessionRevoker).revokeAll(anyString(), any());

        Cookie session = login();

        // 예전에는 AfterCommit 콜백의 예외가 그대로 전파되어 500이 됐다 — DB는 이미
        // 커밋됐는데 클라이언트는 실패로 알고 옛 비밀번호로 재시도할 수 있었다.
        mockMvc.perform(put("/api/v1/users/me/password")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isNoContent());

        // 세션 폐기는 실패했지만 비밀번호 변경 자체는 이미 커밋되어 있다.
        User reloaded = userRepository.findByEmailHash(EmailHasher.sha512Hex(EMAIL)).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword123!", reloaded.getPassword())).isTrue();
    }

    private Cookie login() throws Exception {
        Cookie cookie = mockMvc.perform(post("/login")
                        .param("username", EMAIL)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");

        assertThat(cookie).as("로그인 후 SESSION 쿠키가 내려와야 한다").isNotNull();
        return cookie;
    }
}
