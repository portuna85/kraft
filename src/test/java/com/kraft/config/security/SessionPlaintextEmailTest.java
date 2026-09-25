package com.kraft.config.security;

import com.kraft.post.domain.PostRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 세션을 JDBC 세션 테이블에 저장할 때 이메일 원문이 남지 않는지 실제 저장 바이트로 확인한다
 * (평가 보고서 2026-09-25 F01).
 * <p>
 * 예전에는 {@link KraftUserDetails}가 이메일을 필드로 들고 있어, 직렬화된 SecurityContext
 * ({@code SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_BYTES})에 이메일이 평문으로 들어갔다 —
 * {@code users.email}을 컬럼 암호화해도 세션 테이블·그 백업을 읽을 수 있으면 복원됐다.
 * principal 이름이 회원 id라는 것만 보는 검사는 객체 내부 필드를 놓치므로, 저장된 BLOB 자체를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionPlaintextEmailTest {

    private static final String EMAIL = "session-plaintext-probe@example.com";
    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        userRepository.deleteAll();
        userId = userRepository.save(User.builder()
                .name("세션점검")
                .email(EMAIL)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .build()).getId();
    }

    @Test
    @DisplayName("F01: 로그인 후 저장된 세션 속성 바이트에 이메일 원문이 없다")
    void storedSessionAttributes_doNotContainPlaintextEmail() throws Exception {
        login();

        List<byte[]> blobs = jdbcTemplate.query("""
                        SELECT a.ATTRIBUTE_BYTES FROM SPRING_SESSION_ATTRIBUTES a
                        JOIN SPRING_SESSION s ON s.PRIMARY_ID = a.SESSION_PRIMARY_ID
                        WHERE s.PRINCIPAL_NAME = ?""",
                (rs, i) -> rs.getBytes(1), String.valueOf(userId));

        assertThat(blobs).as("로그인한 세션의 속성이 저장돼 있어야 검사가 의미 있다").isNotEmpty();
        byte[] needle = EMAIL.getBytes(StandardCharsets.UTF_8);
        for (byte[] blob : blobs) {
            assertThat(indexOf(blob, needle)).as("세션 BLOB에 이메일 원문이 있으면 안 된다").isEqualTo(-1);
        }
    }

    @Test
    @DisplayName("F01: 이메일 없는 principal로도 글쓰기·글 상세 화면(작성 가능 여부 판정)이 동작한다")
    void sessionWithoutEmail_stillAuthorizesAndRendersPostPage() throws Exception {
        Cookie session = login();

        String location = mockMvc.perform(post("/api/v1/posts")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"내용\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long postId = Long.parseLong(location.replaceAll("\\D", ""));

        mockMvc.perform(get("/posts/update/" + postId).cookie(session))
                .andExpect(status().isOk());
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
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
