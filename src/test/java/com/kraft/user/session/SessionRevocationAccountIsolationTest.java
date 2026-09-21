package com.kraft.user.session;

import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B02: 탈퇴 후 같은 이메일로 재가입한 계정의 세션이, 옛 계정의 지연된 세션 폐기 태스크로 잘못
 * 지워지지 않는지 <b>실제</b> 세션 저장소·로그인 흐름으로 검증한다. {@link SessionRevoker}를
 * 목으로 대체하지 않는다 — 세션 속성({@code KRAFT_USER_ID}) 기반 계정 구분 자체가 검증 대상이다.
 * <p>
 * {@link SessionRevocationWorkerTest#staleTaskUsesTheSnapshottedEmailNotTheCurrentAccountEmail}은
 * 태스크가 스냅샷 이메일을 쓴다는 것만 증명할 뿐, 실제 세션 저장소에서 다른 계정의 세션이
 * 안전한지는 증명하지 않는다 — 그 간극을 이 테스트가 메운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionRevocationAccountIsolationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRevocationStore store;

    @Autowired
    private SessionRevocationWorker worker;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void setUp() {
        email = "isolation-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @Test
    @DisplayName("B02: 탈퇴 후 재가입한 계정의 세션은 옛 계정의 지연된 폐기 태스크로 지워지지 않는다")
    void staleRevocationTaskDoesNotRevokeTheNewAccountsSession() throws Exception {
        User oldAccount = userRepository.save(User.builder()
                .name("old-account")
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .build());
        Cookie oldSession = login(email);

        // 아직 처리되지 않은 지연된 태스크를 만든다 — 실제로는 커밋 직후 빠른 경로가 실패했을 때
        // 생기는 상황이다. 일부러 attemptNow를 바로 부르지 않고 재가입까지 미뤄 둔다.
        Long taskId = store.enqueue(oldAccount, email);

        oldAccount.withdraw("withdrawn-" + oldAccount.getId() + "@kraft.invalid", "탈퇴한 사용자", "encoded");
        userRepository.save(oldAccount);

        userRepository.save(User.builder()
                .name("new-account")
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .build());
        Cookie newSession = login(email);

        worker.attemptNow(taskId);

        assertThat(canWritePost(oldSession)).as("옛 계정 세션은 폐기되어야 한다").isFalse();
        assertThat(canWritePost(newSession)).as("재가입한 새 계정 세션은 유지되어야 한다").isTrue();
    }

    private Cookie login(String loginEmail) throws Exception {
        Cookie cookie = mockMvc.perform(post("/login")
                        .param("username", loginEmail)
                        .param("password", PASSWORD)
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
