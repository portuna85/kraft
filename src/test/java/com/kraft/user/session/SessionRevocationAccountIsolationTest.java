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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B02: 탈퇴 후 같은 이메일로 재가입한 계정의 세션이, 옛 계정의 지연된 세션 폐기 태스크로 잘못
 * 지워지지 않는지 <b>실제</b> 세션 저장소·로그인 흐름으로 검증한다. {@link SessionRevoker}를
 * 목으로 대체하지 않는다 — 세션 속성({@code KRAFT_USER_ID}) 기반 계정 구분 자체가 검증 대상이다.
 * <p>
 * {@link SessionRevocationWorkerTest#staleTaskUsesTheOriginalUserIdNotTheNewAccount}은
 * 태스크가 원래 계정의 회원 번호로만 폐기를 시도한다는 것만 증명할 뿐, 실제 세션 저장소에서
 * 다른 계정의 세션이 안전한지는 증명하지 않는다 — 그 간극을 이 테스트가 메운다.
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

    /**
     * COR-02 회귀: 폐기 태스크가 아직 <b>실행되기 전</b>(enqueue조차 하지 않은, 순수하게
     * 옛 세션이 아직 살아 있는 시점) 옛 계정의 세션으로 같은 이메일의 새 계정의 글을
     * 관리(수정·삭제)할 수 있으면 안 된다. 옛 방식(이메일 문자열 비교)은 두 계정이 같은
     * 이메일을 공유하는 이 창에서 소유자 판정이 그대로 통과했다 — OwnershipPolicy가 이제는
     * 로그인 시점에 세션에 고정된 userId로 판정하므로, 이메일이 같아도 다른 계정으로 남는다.
     */
    @Test
    @DisplayName("COR-02 회귀: 탈퇴·재가입 사이 옛 세션은 같은 이메일 새 계정의 글을 관리할 수 없다")
    void staleSessionCannotManageNewAccountsPostEvenWithSameEmail() throws Exception {
        userRepository.save(User.builder()
                .name("old-account-2")
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .build());
        Cookie oldSession = login(email);

        User oldAccount = userRepository.findByEmailHash(com.kraft.user.domain.EmailHasher.sha512Hex(email)).orElseThrow();
        oldAccount.withdraw("withdrawn-" + oldAccount.getId() + "@kraft.invalid", "탈퇴한 사용자" + oldAccount.getId(), "encoded");
        userRepository.save(oldAccount);

        userRepository.save(User.builder()
                .name("new-account-2")
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .build());
        Cookie newSession = login(email);

        Long postId = createPost(newSession, "새 계정 글");

        // 옛 세션은 새 계정의 글을 수정·삭제할 수 없어야 한다(둘 다 같은 이메일이라도).
        // 수정은 PostService.update가 소유권을 보기 전에 작성자(옛 계정, 탈퇴 상태) 조회부터
        // 막혀 404다(CurrentUser.require가 NotFoundException을 던진다, BE-07) — delete는
        // 작성자 조회 없이 소유권만 보므로 403이다. 상태 코드는 다르지만 둘 다 실제로
        // 거절된다는 점이 이 테스트의 핵심이다.
        int updateStatus = mockMvc.perform(put("/api/v1/posts/" + postId)
                        .cookie(oldSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"가로채기\",\"content\":\"가로채기 내용\",\"version\":0}"))
                .andReturn().getResponse().getStatus();
        assertThat(updateStatus).as("옛 세션은 새 계정의 글을 수정할 수 없어야 한다").isEqualTo(404);

        int deleteStatus = mockMvc.perform(delete("/api/v1/posts/" + postId)
                        .cookie(oldSession)
                        .with(csrf()))
                .andReturn().getResponse().getStatus();
        assertThat(deleteStatus).as("옛 세션은 새 계정의 글을 삭제할 수 없어야 한다").isEqualTo(403);

        // 새 계정 세션은 정상적으로 자기 글을 관리할 수 있어야 한다.
        assertThat(canWritePost(newSession)).as("새 계정 세션은 계속 정상 동작해야 한다").isTrue();

        // 탈퇴한 옛 세션은 새 글조차 쓸 수 없어야 한다 — CurrentUser가 userId로 조회한 계정이
        // 탈퇴 상태이므로 거절된다(폐기 태스크가 아직 돌지 않았어도).
        assertThat(canWritePost(oldSession)).as("탈퇴한 옛 세션은 새 글을 쓸 수 없어야 한다").isFalse();
    }

    private Long createPost(Cookie sessionCookie, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/posts")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"내용\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Long.valueOf(body);
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
