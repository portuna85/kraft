package com.kraft.post.web;

import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검색·링크 미리보기 메타데이터, 서버 렌더링 본문, 색인 정책(평가 보고서 2026-09-25 F08).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeoMetadataTest {

    private static final String EVIL_TITLE = "<script>alert('t')</script>\"><b>제목";
    private static final String EVIL_CONTENT = "첫 줄\n\n</title><script>alert('c')</script>\t둘째 줄 " + "가".repeat(200);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    private Long postId;

    @BeforeEach
    void setUp() {
        User author = userRepository.save(User.builder()
                .name("seo-" + UUID.randomUUID().toString().substring(0, 8))
                .email("seo-" + UUID.randomUUID() + "@example.com")
                .password("x")
                .role(Role.USER)
                .build());
        postId = postRepository.save(Post.builder()
                .title(EVIL_TITLE)
                .content(EVIL_CONTENT)
                .user(author)
                .build()).getId();
    }

    @Test
    @DisplayName("게시글: JS 없이 읽을 본문과 제목이 초기 HTML에 있고, 사용자 입력은 모두 이스케이프된다")
    void postPage_rendersEscapedServerSideBody() throws Exception {
        String html = page("/posts/update/" + postId, null);

        assertThat(html).contains("data-ssr-content");
        assertThat(html).contains("&lt;script&gt;alert(&#39;t&#39;)&lt;/script&gt;&quot;&gt;&lt;b&gt;제목");
        assertThat(html).contains("&lt;/title&gt;&lt;script&gt;alert(&#39;c&#39;)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>alert(");
        assertThat(html).doesNotContain("<b>제목");
    }

    @Test
    @DisplayName("게시글: description은 본문 앞부분(공백 정리·150자), canonical·og:url은 설정된 대표 주소 기준이다")
    void postPage_hasMetadataFromConfiguredBaseUrl() throws Exception {
        String html = page("/posts/update/" + postId, null);
        String canonical = baseUrl.replaceAll("/$", "") + "/posts/update/" + postId;

        assertThat(html).contains("<link rel=\"canonical\" href=\"" + canonical + "\"");
        assertThat(html).contains("<meta property=\"og:url\" content=\"" + canonical + "\"");
        assertThat(html).contains("<meta property=\"og:type\" content=\"article\"");
        assertThat(html).contains("<meta name=\"description\" content=\"첫 줄 &lt;/title&gt;&lt;script&gt;alert(&#39;c&#39;)&lt;/script&gt; 둘째 줄 가");
        assertThat(html).contains("…\"");
    }

    @Test
    @DisplayName("대표 주소는 요청의 Host 헤더를 따르지 않는다")
    void canonical_ignoresHostHeader() throws Exception {
        String html = page("/posts/update/" + postId, "evil.example");

        assertThat(html).doesNotContain("evil.example");
        assertThat(html).contains(baseUrl.replaceAll("/$", "") + "/posts/update/" + postId);
    }

    @Test
    @DisplayName("목록: 검색하지 않은 첫 페이지만 대표 경로를 선언한다")
    void index_canonicalOnlyForUnfilteredFirstPage() throws Exception {
        String base = baseUrl.replaceAll("/$", "");
        // 2쪽이 실제로 있어야 한다 — 없는 쪽을 요청하면 마지막 쪽으로 리다이렉트된다.
        User author = postRepository.findById(postId).orElseThrow().getUser();
        for (int i = 0; i < 10; i++) {
            postRepository.save(Post.builder().title("목록 " + i).content("내용").user(author).build());
        }

        assertThat(page("/", null)).contains("<link rel=\"canonical\" href=\"" + base + "/\"");
        assertThat(page("/?category=QNA", null)).contains("<link rel=\"canonical\" href=\"" + base + "/?category=QNA\"");
        assertThat(page("/?q=abc", null)).doesNotContain("rel=\"canonical\"");
        assertThat(page("/?page=1", null)).doesNotContain("rel=\"canonical\"");
    }

    @Test
    @DisplayName("색인하지 않을 화면·API에는 X-Robots-Tag: noindex가 붙고, 공개 화면에는 붙지 않는다")
    void noindexHeader_onlyOnPrivatePages() throws Exception {
        for (String path : new String[] { "/login", "/signup", "/forgot-password", "/users/verify?token=x", "/posts/save" }) {
            mockMvc.perform(get(path)).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        }
        mockMvc.perform(get("/api/v1/posts")).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        mockMvc.perform(get("/")).andExpect(header().doesNotExist("X-Robots-Tag"));
        mockMvc.perform(get("/posts/update/" + postId)).andExpect(header().doesNotExist("X-Robots-Tag"));
        mockMvc.perform(get("/recommend")).andExpect(header().doesNotExist("X-Robots-Tag"));
    }

    @Test
    @DisplayName("robots.txt는 관리자·API만 막는다")
    void robotsTxt_isServed() throws Exception {
        String body = mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("User-agent: *").contains("Disallow: /admin").contains("Disallow: /api/");
        assertThat(body).doesNotContain("Disallow: /\n");
    }

    private String page(String path, String host) throws Exception {
        var request = get(path);
        if (host != null) {
            request = request.header("Host", host);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
