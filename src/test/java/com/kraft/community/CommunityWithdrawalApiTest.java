package com.kraft.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.Application;
import com.kraft.community.auth.CommunityPrincipal;
import com.kraft.community.comment.CommunityComment;
import com.kraft.community.comment.CommunityCommentRepository;
import com.kraft.community.comment.CreateCommentRequest;
import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.post.CreatePostRequest;
import com.kraft.community.post.PostStatus;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        classes = Application.class,
        properties = {
            "kraft.community.write-rate-limit-per-minute=2000",
            "kraft.security.rate-limit-per-minute=2000"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommunityWithdrawalApiTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private CommunityUserRepository communityUserRepository;
    @Autowired private CommunityPostRepository communityPostRepository;
    @Autowired private CommunityCommentRepository communityCommentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CommunityUser owner;

    @BeforeEach
    void setUp() {
        owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "owner", null, OffsetDateTime.now()));
    }

    @Test
    void unauthenticatedRequestCannotWithdraw() throws Exception {
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawalDeletesAccountAndTombstonesAuthoredContent() throws Exception {
        long postId = createPost(owner, "title", "content");
        long commentId = createComment(owner, postId, "comment");
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(asUser(owner)).with(csrf())).andExpect(status().isNoContent());

        assertThat(communityUserRepository.findById(owner.getId())).isEmpty();
        CommunityPost post = communityPostRepository.findById(postId).orElseThrow();
        assertThat(post.getOwnerId()).isNull();
        assertThat(post.getAuthorNameSnapshot()).isEqualTo("삭제된 사용자");
        assertThat(post.getTitle()).isEqualTo("삭제된 게시글입니다.");
        assertThat(post.getContent()).isEqualTo("삭제된 게시글입니다.");
        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
        CommunityComment comment = communityCommentRepository.findById(commentId).orElseThrow();
        assertThat(comment.getOwnerId()).isNull();
        assertThat(comment.getAuthorNameSnapshot()).isEqualTo("삭제된 사용자");
        assertThat(comment.getContent()).isEqualTo("삭제된 댓글입니다.");
        assertThat(comment.isDeleted()).isTrue();
    }

    @Test
    void staleSessionIsRejectedAfterWithdrawal() throws Exception {
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(asUser(owner)).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/community/posts").with(asUser(owner)).with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreatePostRequest("title", "content", "GENERAL", null))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMMUNITY_ACCOUNT_DELETED"));
    }

    @Test
    @DisplayName("BE-PERF-02: 탈퇴 후 stale 세션의 GET 요청은 의도적으로 통과한다(쓰기만 차단)")
    void staleSessionGetRequestPassesThroughAfterWithdrawal() throws Exception {
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(asUser(owner)).with(csrf())).andExpect(status().isNoContent());

        // CommunityWithdrawnAccountFilter는 GET/HEAD/OPTIONS를 shouldNotFilter로 건너뛴다 — 매
        // 읽기 요청마다 existsById를 실행하지 않기 위한 의도된 트레이드오프(BE-PERF-02).
        // 인증이 필요한 GET 엔드포인트(/me/blocked-users)로 확인한다 — 게시글 GET은 permitAll이라
        // 이 필터의 인증 분기 자체를 타지 않으므로 검증에 부적합하다.
        mockMvc.perform(get("/api/v1/community/me/blocked-users").with(asUser(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void withdrawalClearsSessionCookieWithSafeAttributes() throws Exception {
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(asUser(owner)).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(cookie().httpOnly("JSESSIONID", true)).andExpect(cookie().secure("JSESSIONID", true))
                .andExpect(cookie().path("JSESSIONID", "/"));
    }

    @Test
    void staleSessionCannotWithdrawTwice() throws Exception {
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(asUser(owner)).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(asUser(owner)).with(csrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMMUNITY_ACCOUNT_DELETED"));
        assertThat(communityUserRepository.findById(owner.getId())).isEmpty();
    }

    private long createPost(CommunityUser author, String title, String content) throws Exception {
        String body = mockMvc.perform(post("/api/v1/community/posts").with(asUser(author)).with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreatePostRequest(title, content, "GENERAL", null))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createComment(CommunityUser author, long postId, String content) throws Exception {
        String body = mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .with(asUser(author)).with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest(content, null))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private RequestPostProcessor asUser(CommunityUser user) {
        CommunityPrincipal principal = new CommunityPrincipal(user.getId(), user.getNickname());
        Authentication authenticated = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(authenticated);
    }
}
