package com.kraft.user.service;

import com.kraft.post.domain.PostRepository;
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
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비밀번호를 바꾸면 서버가 그 계정의 세션을 실제로 폐기하는지 검증한다.
 * <p>
 * 서버가 비밀번호 해시만 갱신하고 로그아웃을 화면 JS가 이어서 호출하는 {@code /logout}에 맡기면, 실제 로그인으로 받은 세션 쿠키로 비밀번호를 바꾼 뒤 <b>같은 쿠키로 새 글을 쓸 수 있다</b> — 세션을 탈취당한 상태에서 비밀번호를 바꿔도 접근 회수가 되지 않고 다른 기기의 세션도 끊기지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeSessionRevocationTest {

    // 세션 테이블(SPRING_SESSION)은 테스트 클래스끼리 공유하는 H2에 있다. 다른 테스트가 남긴 세션이 섞이지 않도록 이 클래스 전용 이메일을 쓰고 매번 비우고 시작하며, principal 이름은 회원 id라 setUp에서 만든 계정의 id를 조회해 정리한다.
    private static final String EMAIL = "password-change@example.com";
    private static final String PASSWORD = "Password123!";
    private Long userId;

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

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @BeforeEach
    void setUp() {
        // users를 참조하는 것들을 먼저 지운다. 하나라도 빠뜨리면 FK 위반으로 깨지는데, 그 시점이 테스트 실행 순서에 좌우되어 관계없는 변경에서 갑자기 드러난다.
        postRepository.deleteAll();
        outboxMailRepository.deleteAll();
        tokenRepository.deleteAll();
        sessionRevocationTaskRepository.deleteAll();
        userRepository.deleteAll();
        userId = userRepository.save(User.builder()
                .name("tester")
                .email(EMAIL)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .build()).getId();
        // id가 재사용될 가능성은 없지만(IDENTITY 증가) 다른 테스트가 남긴 세션과 섞이지 않도록 비우고 시작한다.
        sessionRepository.findByPrincipalName(String.valueOf(userId)).keySet().forEach(sessionRepository::deleteById);
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 변경에 쓴 그 세션으로 더는 글을 쓸 수 없다")
    void changePassword_revokesTheSessionUsedForTheChange() throws Exception {
        Cookie session = login();

        // 변경 전에는 이 세션으로 글을 쓸 수 있다.
        assertThat(canWritePost(session)).isTrue();

        mockMvc.perform(put("/api/v1/users/me/password")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isNoContent());

        assertThat(canWritePost(session)).isFalse();
    }

    @Test
    @DisplayName("다른 기기의 세션도 함께 폐기된다")
    void changePassword_revokesSessionsOnOtherDevices() throws Exception {
        Cookie phone = login();
        Cookie laptop = login();
        assertThat(sessionRepository.findByPrincipalName(String.valueOf(userId))).hasSize(2);

        mockMvc.perform(put("/api/v1/users/me/password")
                        .cookie(laptop)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isNoContent());

        assertThat(sessionRepository.findByPrincipalName(String.valueOf(userId))).isEmpty();
        assertThat(canWritePost(phone)).isFalse();
    }

    @Test
    @DisplayName("현재 비밀번호가 틀려 변경이 실패하면 세션은 그대로 유지된다")
    void changePassword_whenCurrentPasswordIsWrong_keepsSession() throws Exception {
        Cookie session = login();

        mockMvc.perform(put("/api/v1/users/me/password")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPassword1!\",\"newPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isBadRequest());

        assertThat(canWritePost(session)).isTrue();
    }

    @Test
    @DisplayName("새 비밀번호로 다시 로그인하면 정상적으로 쓸 수 있다")
    void changePassword_allowsLoginWithNewPassword() throws Exception {
        Cookie session = login();

        mockMvc.perform(put("/api/v1/users/me/password")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isNoContent());

        assertThat(canWritePost(login("NewPassword123!"))).isTrue();
    }

    /** 실제 폼 로그인으로 세션을 만들고 브라우저가 들고 다니는 것과 같은 SESSION 쿠키를 돌려준다. 세션 저장소가 Spring Session JDBC라 세션 상태는 서블릿 컨테이너가 아니라 DB에 있으므로, 후속 요청은 이 쿠키로 세션을 되찾아야 한다. */
    private Cookie login() throws Exception {
        return login(PASSWORD);
    }

    private Cookie login(String password) throws Exception {
        Cookie cookie = mockMvc.perform(post("/login")
                        .param("username", EMAIL)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");

        assertThat(cookie).as("로그인 후 SESSION 쿠키가 내려와야 한다").isNotNull();
        return cookie;
    }

    /** 인증이 필요한 실제 엔드포인트를 호출해 세션이 아직 유효한지 본다. */
    private boolean canWritePost(Cookie sessionCookie) throws Exception {
        int status = mockMvc.perform(post("/api/v1/posts")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"내용\"}"))
                .andReturn()
                .getResponse()
                .getStatus();
        return status == 200;
    }
}
