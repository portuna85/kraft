package com.kraft.config.security;

import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    void 로그인_성공시_redirect_파라미터_위치로_이동한다() throws Exception {
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
    void 로그인_성공시_redirect_없으면_루트로_이동한다() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "tester@example.com")
                        .param("password", "Password123!")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("로그인 성공: redirect가 외부 절대 URL이면 무시하고 \"/\"로 이동한다(오픈 리다이렉트 방지)")
    void 로그인_성공시_redirect가_외부_URL이면_무시한다() throws Exception {
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
    void 로그인_성공시_redirect가_프로토콜_상대_URL이면_무시한다() throws Exception {
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
    void 게시글등록_화면에서_로그아웃하면_루트로_이동한다() throws Exception {
        mockMvc.perform(post("/logout")
                        .header("Referer", "http://localhost/posts/save")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("로그아웃: 비밀번호 변경 화면(Referer)에서 로그아웃하면 \"/login\"으로 이동한다")
    void 비밀번호변경_화면에서_로그아웃하면_로그인으로_이동한다() throws Exception {
        mockMvc.perform(post("/logout")
                        .header("Referer", "http://localhost/users/me/password")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("로그아웃: 그 외 화면(Referer)에서 로그아웃하면 Referer로 되돌아간다")
    void 그외_화면에서_로그아웃하면_Referer로_이동한다() throws Exception {
        mockMvc.perform(post("/logout")
                        .header("Referer", "http://localhost/")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/"));
    }
}
