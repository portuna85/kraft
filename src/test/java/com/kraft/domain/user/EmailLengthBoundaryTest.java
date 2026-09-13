package com.kraft.domain.user;

import com.kraft.domain.post.PostRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이메일 길이 경계를 가입부터 로그인·세션 저장까지 한 번에 통과시켜 검증한다
 * (개선 보고서 F07).
 * <p>
 * 예전에는 세 경계가 서로 달랐다: 입력 검증에는 길이 제한이 없었고, 암호화 컬럼
 * {@code users.email VARCHAR(500)}은 hex 암호문({@code 평문 × 2 + 64}) 때문에 218자까지만
 * 받았으며, 세션의 {@code PRINCIPAL_NAME}은 100자였다. 그 결과 101~218자 이메일은
 * <b>가입은 성공하는데 로그인 세션 저장에서 실패하는</b> 계정이 됐다.
 * <p>
 * 이제 {@link EmailPolicy#MAX_LENGTH}(가장 좁은 경계)를 제품 정책으로 삼아 가입 시점에
 * 거부한다. 이 테스트는 "정책 상한 길이는 끝까지 동작하고, 한 자만 넘으면 가입 단계에서
 * 명확한 400"임을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmailLengthBoundaryTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private EmailAttributeConverter emailAttributeConverter;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        // 가입하면 인증 토큰이 함께 생기므로, users보다 먼저 지워야 FK에 걸리지 않는다.
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("F07: 정책 상한 길이의 이메일은 가입·로그인·세션 저장·글쓰기까지 전부 동작한다")
    void maxLengthEmail_worksThroughSignupLoginAndSession() throws Exception {
        String email = emailOfLength(EmailPolicy.MAX_LENGTH);
        assertThat(email).hasSize(EmailPolicy.MAX_LENGTH);

        signUp(email).andExpect(status().isOk());

        // 예전에는 여기(세션 저장)에서 PRINCIPAL_NAME 컬럼 제한에 걸렸다.
        Cookie session = login(email);
        assertThat(sessionRepository.findByPrincipalName(email)).hasSize(1);

        mockMvc.perform(post("/api/v1/posts")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"내용\"}"))
                // GUEST(이메일 미인증)라 글쓰기는 막히지만, 그것은 인증이 아니라 권한 문제다 —
                // 세션이 살아 있다는 뜻이므로 로그인 리다이렉트(302)가 아니어야 한다.
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("F07: 정책 상한 길이 이메일의 암호문이 users.email 컬럼 안에 들어간다")
    void maxLengthEmail_cipherTextFitsInColumn() {
        String cipher = emailAttributeConverter.convertToDatabaseColumn(emailOfLength(EmailPolicy.MAX_LENGTH));

        // 암호문 길이 = 평문 × 2 + 64 (AES-GCM + hex). 컬럼은 VARCHAR(500)이다.
        assertThat(cipher).hasSize(EmailPolicy.MAX_LENGTH * 2 + 64);
        assertThat(cipher.length()).isLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("F07: 정책 상한을 한 자 넘으면 가입 단계에서 400으로 거부한다")
    void tooLongEmail_isRejectedAtSignup() throws Exception {
        signUp(emailOfLength(EmailPolicy.MAX_LENGTH + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "email: 이메일은 " + EmailPolicy.MAX_LENGTH + "자 이하로 입력하세요."));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("F07: 예전에 '가입은 되고 로그인은 안 되던' 길이(101~218자)는 이제 가입 자체가 막힌다")
    void previouslyUnusableLengths_areRejectedAtSignup() throws Exception {
        for (int length : new int[]{101, 150, 218, 219}) {
            signUp(emailOfLength(length)).andExpect(status().isBadRequest());
        }

        assertThat(userRepository.count()).isZero();
    }

    /**
     * 정확히 {@code length}자이면서 {@code @Email} 검증을 통과하는 주소를 만든다.
     * <p>
     * 단순히 "a"를 길게 이어 붙이면 안 된다 — {@code @Email}은 local part를 64자로,
     * 도메인 라벨 하나를 63자로 제한하므로 그런 문자열은 길이가 아니라 <b>형식</b> 위반으로
     * 걸린다. 그러면 길이 정책을 검증하는 게 아니라 형식 검증을 검증하게 된다.
     */
    private static String emailOfLength(int length) {
        String local = "a".repeat(Math.min(64, length - 6));
        String email = local + "@" + domainOfLength(length - local.length() - 1);

        assertThat(email).as("테스트가 만든 주소의 길이").hasSize(length);
        return email;
    }

    /** {@code aaa.aaa.com}처럼 라벨을 점으로 이어 정확히 {@code length}자인 도메인을 만든다. */
    private static String domainOfLength(int length) {
        int remaining = length - ".com".length();
        StringBuilder domain = new StringBuilder();
        while (remaining > 0) {
            int chunk = Math.min(40, remaining);
            if (remaining - chunk == 1) {
                // 점만 남고 라벨이 비면 ".."이 되어 형식 위반이 된다.
                chunk--;
            }
            domain.append("a".repeat(chunk));
            remaining -= chunk;
            if (remaining > 0) {
                domain.append('.');
                remaining--;
            }
        }
        return domain + ".com";
    }

    private ResultActions signUp(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"tester\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private Cookie login(String email) throws Exception {
        Cookie cookie = mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");

        assertThat(cookie).as("로그인에 성공하면 SESSION 쿠키가 내려와야 한다").isNotNull();
        return cookie;
    }
}
