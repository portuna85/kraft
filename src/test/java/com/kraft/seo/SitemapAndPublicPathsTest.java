package com.kraft.seo;

import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** sitemap·robots·색인 제어(P1-1), 공개 경로 화이트리스트(P1-7), 익명 세션 미생성(P1-6). */
@SpringBootTest
@AutoConfigureMockMvc
class SitemapAndPublicPathsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Test
    @DisplayName("sitemap에는 홈·추천·게시글만 들어 있다")
    void sitemap_listsPublicUrlsOnly() throws Exception {
        User author = userRepository.save(User.builder()
                .name("sm-" + UUID.randomUUID().toString().substring(0, 8))
                .email("sm-" + UUID.randomUUID() + "@example.com").password("x").role(Role.USER).build());
        Long postId = postRepository.save(Post.builder().title("t").content("c").user(author).build()).getId();

        MvcResult result = mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andReturn();

        String xml = result.getResponse().getContentAsString();
        assertThat(xml).startsWith("<?xml").contains("<urlset")
                .contains("/posts/update/" + postId + "</loc>")
                .contains("<loc>http")
                .doesNotContain("/admin").doesNotContain("/login").doesNotContain("/api/");
        assertThat(xml).contains("/recommend</loc>");
    }

    @Test
    @DisplayName("robots.txt는 sitemap을 선언하고 검색 결과를 막지 않는다")
    void robots_declaresSitemap() throws Exception {
        String body = mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Sitemap: ").contains("/sitemap.xml")
                .contains("Disallow: /admin").contains("Disallow: /api/")
                .doesNotContain("q=");
    }

    @Test
    @DisplayName("검색 결과 페이지는 noindex,follow 헤더를 받고 일반 목록은 받지 않는다")
    void searchResults_areNoindex() throws Exception {
        mockMvc.perform(get("/").param("q", "abc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex, follow"));
        MvcResult plain = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        assertThat(plain.getResponse().getHeader("X-Robots-Tag")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/recommend", "/login", "/signup", "/forgot-password", "/healthz",
            "/robots.txt", "/sitemap.xml", "/posts/save"})
    @DisplayName("공개 경로는 익명에게 열려 있고 세션을 만들지 않는다")
    void publicPaths_openAndSessionless(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getRequest().getSession(false)).as("익명 GET %s가 세션을 만들었다", path).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/reports", "/some-new-endpoint", "/api/v1/users/me/ping"})
    @DisplayName("공개 목록에 없는 경로는 익명에게 열리지 않고 세션도 만들지 않는다")
    void unlistedPaths_requireAuthentication(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andReturn();

        assertThat(result.getResponse().getStatus()).isIn(302, 401, 403);
        assertThat(result.getRequest().getSession(false)).isNull();
    }
}
