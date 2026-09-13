package com.kraft.config.security;

import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SecurityConfig}의 로그인/로그아웃 리다이렉트 정책 통합 테스트. 실제 필터 체인·
 * {@code UserDetailsService}가 필요해 {@code @SpringBootTest}로 전체 컨텍스트를 띄운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .name("tester")
                .email("tester@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.USER)
                .build());
    }

    @Test
    @DisplayName("로그인 성공: redirect 파라미터로 넘어온 원래 페이지로 이동한다")
    void loginSuccess_redirectsToTargetUrl() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "tester@example.com")
                        .param("password", "Password123!")
                        .param("redirect", "/posts/save")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/save"));
    }

    @Test
    @DisplayName("로그인 성공: redirect 파라미터가 없으면 \"/\"로 이동한다")
    void loginSuccess_withoutRedirectParam_redirectsToRoot() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "tester@example.com")
                        .param("password", "Password123!")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("로그인 성공: redirect가 외부 절대 URL이면 무시하고 \"/\"로 이동한다(오픈 리다이렉트 방지)")
    void loginSuccess_withExternalRedirectUrl_redirectsToRoot() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "tester@example.com")
                        .param("password", "Password123!")
                        .param("redirect", "https://evil.example.com")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("로그인 성공: redirect가 //로 시작하는 프로토콜 상대 URL이면 무시하고 \"/\"로 이동한다")
    void loginSuccess_withProtocolRelativeRedirectUrl_redirectsToRoot() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "tester@example.com")
                        .param("password", "Password123!")
                        .param("redirect", "//evil.example.com")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("로그아웃: 게시글 등록 화면(Referer)에서 로그아웃하면 \"/\"로 이동한다")
    void logout_fromPostSavePage_redirectsToRoot() throws Exception {
        mockMvc.perform(post("/logout")
                        .header("Referer", "http://localhost/posts/save")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("로그아웃: next 파라미터가 있으면 Referer보다 우선해 그곳으로 이동한다(비밀번호 변경 모달 경로)")
    void logout_withNextParameter_redirectsToThatPath() throws Exception {
        mockMvc.perform(post("/logout")
                        .param("next", "/login")
                        .header("Referer", "http://localhost/")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("로그아웃: next가 외부 URL이면 무시하고 기본값 \"/\"로 보낸다")
    void logout_withExternalNextParameter_ignoresIt() throws Exception {
        mockMvc.perform(post("/logout")
                        .param("next", "//evil.example.com")
                        .header("Referer", "http://localhost/")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("로그아웃: next가 비어 있으면 Referer 규칙을 따른다")
    void logout_withBlankNextParameter_followsRefererRule() throws Exception {
        mockMvc.perform(post("/logout")
                        .param("next", "")
                        .header("Referer", "http://localhost/posts/save")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("로그아웃: 그 외 화면(Referer)에서 로그아웃하면 그 화면의 경로로 되돌아간다")
    void logout_fromOtherPages_redirectsToReferer() throws Exception {
        mockMvc.perform(post("/logout")
                        .header("Referer", "http://localhost/posts/update/1?x=1")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                // 원본 Referer 문자열이 아니라 경로만 쓴다 — 오리진 판정과 실제 이동 대상이
                // 어긋날 여지를 남기지 않기 위해서다(SafeRedirect.sameOriginPathOf).
                .andExpect(redirectedUrl("/posts/update/1?x=1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 예전의 startsWith("/") && !startsWith("//") 검사를 통과하던 값이다.
            // 브라우저는 역슬래시를 "/"와 같게 읽어 attacker.example을 호스트로 삼는다.
            "/\\attacker.example/path",
            "//evil.example.com",
            "http://evil.example.com/"
    })
    @DisplayName("로그인 성공: redirect가 외부로 나갈 수 있는 값이면 무시하고 \"/\"로 보낸다")
    void loginSuccess_withExternalRedirect_goesToRoot(String redirect) throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "tester@example.com")
                        .param("password", "Password123!")
                        .param("redirect", redirect)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("로그아웃: next가 역슬래시로 외부 호스트를 가리키면 무시한다")
    void logout_withBackslashNextParameter_goesToRoot() throws Exception {
        mockMvc.perform(post("/logout")
                        .param("next", "/\\attacker.example/path")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("로그아웃: Referer가 호스트 접두어만 같은 외부 도메인이면 신뢰하지 않는다")
    void logout_withLookalikeRefererHost_goesToRoot() throws Exception {
        // startsWith(baseUrl) 문자열 비교는 이 값을 같은 오리진으로 인정했다.
        mockMvc.perform(post("/logout")
                        .header("Referer", "http://localhost.attacker.example/path")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
