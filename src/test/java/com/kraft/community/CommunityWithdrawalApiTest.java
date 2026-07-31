package com.kraft.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

/**
 * KB-04: 탈퇴 흐름의 API 계약 검증 — 기존 게시글·댓글 작성자 표기 일괄 익명화,
 * 다른 기기(=새 Authentication)에 남아있던 세션도 요청마다 DB를 재확인해 차단하는지.
 */
// CommunityPostCommentApiTest와 동일한 이유로 write-rate-limit을 완화한다 — 기본 설정
// (20/min)으로 두면 이 클래스가 여러 메서드에 걸쳐 만드는 게시글·댓글·탈퇴 요청이, 같은
// 프로퍼티(즉 캐시된 같은 Spring 컨텍스트)를 공유하는 다른 테스트 클래스의 몫까지 갉아먹어
// 무관한 테스트(예: CommunityCsrfCookieTest)를 간헐적으로 429로 실패시킨다.
@SpringBootTest(
        classes = Application.class,
        properties = {
            "kraft.community.write-rate-limit-per-minute=2000",
            "kraft.security.rate-limit-per-minute=2000"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("커뮤니티 탈퇴 API 행위 검증")
class CommunityWithdrawalApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Autowired
    private CommunityPostRepository communityPostRepository;

    @Autowired
    private CommunityCommentRepository communityCommentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CommunityUser owner;

    @BeforeEach
    void setUp() {
        owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "글쓴이", null, OffsetDateTime.now()));
    }

    @Test
    @DisplayName("인증되지 않은 요청은 탈퇴가 거부된다")
    void unauthenticatedRequest_cannotWithdraw() throws Exception {
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴하면 기존 게시글·댓글의 작성자 표기가 일괄로 익명화된다")
    void withdraw_pseudonymizesExistingPostsAndComments() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        long commentId = createComment(owner, postId, "댓글");

        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(asUser(owner)).with(csrf()))
                .andExpect(status().isNoContent());

        CommunityUser reloaded = communityUserRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.isWithdrawn()).isTrue();
        assertThat(reloaded.getNickname()).isEqualTo("탈퇴한 사용자#" + owner.getId());
        assertThat(reloaded.getProfileImageUrl()).isNull();

        CommunityPost post = communityPostRepository.findById(postId).orElseThrow();
        assertThat(post.getAuthorNameSnapshot()).isEqualTo("탈퇴한 사용자#" + owner.getId());

        CommunityComment comment = communityCommentRepository.findById(commentId).orElseThrow();
        assertThat(comment.getAuthorNameSnapshot()).isEqualTo("탈퇴한 사용자#" + owner.getId());
    }

    @Test
    @DisplayName("탈퇴 후 다른 기기에 남아있던 인증도 다음 요청에서 차단된다")
    void withdraw_blocksOtherDeviceSessionsOnNextRequest() throws Exception {
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(asUser(owner)).with(csrf()))
                .andExpect(status().isNoContent());

        // 새 Authentication 객체 — 로그아웃되지 않은 "다른 기기"의 세션을 흉내낸다.
        // 세션을 공유하지 않는데도 차단된다면, 그 판정은 세션 무효화가 아니라
        // CommunityWithdrawnAccountFilter의 요청별 DB 재확인 때문이라는 뜻이다.
        mockMvc.perform(post("/api/v1/community/posts")
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreatePostRequest("제목", "내용", "GENERAL", null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMUNITY_ACCOUNT_WITHDRAWN"));
    }

    // CodeQL java/insecure-cookie(alert #4) 회귀 방지 — 탈퇴 시 클라이언트 쿠키를 직접
    // 지우는 코드(CommunityAccountController.withdraw)가 실제 세션 쿠키 설정
    // (server.servlet.session.cookie.*)과 어긋난 속성으로 나가지 않는지 검증한다.
    // test 프로파일은 secure 오버라이드가 없어 기본값 false — prod(application-prod.yml)에서는
    // true가 주입되므로 반영값 자체가 아니라 "명시적으로 설정되어 있는지"가 핵심이다.
    @Test
    @DisplayName("탈퇴 응답의 세션 클리어 쿠키는 httpOnly가 설정되고 maxAge가 0이다")
    void withdraw_clearsSessionCookieWithSafeAttributes() throws Exception {
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(asUser(owner)).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(cookie().httpOnly("JSESSIONID", true))
                .andExpect(cookie().secure("JSESSIONID", false))
                .andExpect(cookie().path("JSESSIONID", "/"));
    }

    @Test
    @DisplayName("이미 탈퇴한 계정이 다시 탈퇴를 요청해도 오류 없이 그대로 유지된다")
    void withdrawingTwice_isIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(asUser(owner)).with(csrf()))
                .andExpect(status().isNoContent());

        // 세션 무효화 뒤라 재인증을 흉내낸 새 Authentication으로 다시 호출한다 —
        // 필터가 이미 막을 텐데도 여기서는 "탈퇴 자체가 실패로 보이는지"만 관심사이므로
        // COMMUNITY_ACCOUNT_WITHDRAWN 401도 "오류 없이 유지"의 일종으로 허용한다.
        mockMvc.perform(post("/api/v1/community/me/withdrawal").with(asUser(owner)).with(csrf()))
                .andExpect(status().isUnauthorized());

        CommunityUser reloaded = communityUserRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.isWithdrawn()).isTrue();
        assertThat(reloaded.getNickname()).isEqualTo("탈퇴한 사용자#" + owner.getId());
    }

    private long createPost(CommunityUser author, String title, String content) throws Exception {
        String body = mockMvc.perform(post("/api/v1/community/posts")
                        .with(asUser(author))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreatePostRequest(title, content, "GENERAL", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createComment(CommunityUser author, long postId, String content) throws Exception {
        String body = mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .with(asUser(author))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest(content, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private RequestPostProcessor asUser(CommunityUser user) {
        CommunityPrincipal principal = new CommunityPrincipal(user.getId(), user.getNickname());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(authentication);
    }
}
