package com.kraft.community;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.Application;
import com.kraft.community.auth.CommunityPrincipal;
import com.kraft.community.post.CreatePostRequest;
import com.kraft.community.report.CreateReportRequest;
import com.kraft.community.report.ReportReason;
import com.kraft.community.report.ReportTargetType;
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
@DisplayName("커뮤니티 좋아요·북마크·신고·차단·카테고리 API 행위 검증")
class CommunityReactionReportBlockApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CommunityUser owner;
    private CommunityUser other;

    @BeforeEach
    void setUp() {
        owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "글쓴이", null, OffsetDateTime.now()));
        other = communityUserRepository.save(new CommunityUser(
                "google", "other-" + System.nanoTime(), "다른사람", null, OffsetDateTime.now()));
    }

    @Test
    @DisplayName("유효하지 않은 카테고리로 글을 쓰면 400 COMMUNITY_CATEGORY_INVALID로 거부된다")
    void createPost_invalidCategory_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/community/posts")
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreatePostRequest("제목", "내용", "NOT_A_CATEGORY", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMUNITY_CATEGORY_INVALID"));
    }

    @Test
    @DisplayName("좋아요를 두 번 눌러도 두 번째는 멱등하게 204로 끝난다")
    void like_twice_isIdempotent() throws Exception {
        long postId = createPost(owner, "제목", "내용");

        mockMvc.perform(put("/api/v1/community/posts/" + postId + "/like")
                        .with(asUser(other))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/community/posts/" + postId + "/like")
                        .with(asUser(other))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/community/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));
    }

    @Test
    @DisplayName("좋아요 취소는 눌러본 적이 없어도 멱등하게 204로 끝난다")
    void unlike_neverLiked_isIdempotentNoOp() throws Exception {
        long postId = createPost(owner, "제목", "내용");

        mockMvc.perform(delete("/api/v1/community/posts/" + postId + "/like")
                        .with(asUser(other))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("동일 대상을 두 번 신고하면 두 번째는 409 REPORT_ALREADY_EXISTS로 거부된다")
    void report_duplicate_isRejected() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        CreateReportRequest request = new CreateReportRequest(ReportTargetType.POST, postId, ReportReason.SPAM);

        mockMvc.perform(post("/api/v1/community/reports")
                        .with(asUser(other))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/community/reports")
                        .with(asUser(other))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("자기 자신을 차단하려 하면 400으로 거부된다")
    void block_self_isRejected() throws Exception {
        mockMvc.perform(put("/api/v1/community/users/" + owner.getId() + "/block")
                        .with(asUser(owner))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMUNITY_SELF_BLOCK_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("다른 사용자를 차단·해제할 수 있고 두 동작 모두 멱등하다")
    void block_andUnblock_areIdempotent() throws Exception {
        mockMvc.perform(put("/api/v1/community/users/" + other.getId() + "/block")
                        .with(asUser(owner))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/community/users/" + other.getId() + "/block")
                        .with(asUser(owner))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/community/users/" + other.getId() + "/block")
                        .with(asUser(owner))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/community/users/" + other.getId() + "/block")
                        .with(asUser(owner))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("개인 반응 상태 API가 좋아요·북마크·차단 목록을 반환한다")
    void meInteractions_returnsLikedBookmarkedAndBlocked() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        mockMvc.perform(put("/api/v1/community/posts/" + postId + "/like").with(asUser(other)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/community/posts/" + postId + "/bookmark").with(asUser(other)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/community/me/interactions")
                        .param("postIds", String.valueOf(postId))
                        .with(asUser(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedPostIds[0]").value(postId))
                .andExpect(jsonPath("$.bookmarkedPostIds[0]").value(postId));
    }

    @Test
    @DisplayName("KB-10: postIds가 100개를 넘으면 400 VALIDATION_ERROR로 거부된다")
    void meInteractions_tooManyPostIds_isRejected() throws Exception {
        String[] postIds = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(String::valueOf)
                .toArray(String[]::new);

        mockMvc.perform(get("/api/v1/community/me/interactions")
                        .param("postIds", postIds)
                        .with(asUser(other)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("소프트 삭제된 게시글도 작성자 본인은 계속 조회할 수 있다")
    void softDeletedPost_stillVisibleToOwner() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        mockMvc.perform(delete("/api/v1/community/posts/" + postId)
                        .param("expectedVersion", "0")
                        .with(asUser(owner))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/community/posts/" + postId).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN_BY_AUTHOR"));
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

    private RequestPostProcessor asUser(CommunityUser user) {
        CommunityPrincipal principal = new CommunityPrincipal(user.getId(), user.getNickname());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(authentication);
    }
}
