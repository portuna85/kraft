package com.kraft.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.Application;
import com.kraft.community.auth.CommunityPrincipal;
import com.kraft.community.comment.CreateCommentRequest;
import com.kraft.community.post.CreatePostRequest;
import com.kraft.community.post.UpdatePostRequest;
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
 * Blitz 71건의 테스트가 보호하던 행위(익명 무세션 조회, 소유권 판정, 낙관적 잠금 409,
 * 1단계 대댓글 강제, tombstone)를 Kraft 커뮤니티 API에 대한 e2e 성격의 검증으로 옮긴다.
 */
@SpringBootTest(
        classes = Application.class,
        // 페이징 테스트가 한 게시글에 50개 넘는 댓글을 빠르게 생성하므로 기본
        // write-rate-limit(분당 20건)과 IP 기반 공개 rate-limit(분당 120건)을 모두 완화한다 —
        // MockMvc 요청은 전부 127.0.0.1에서 오므로 후자를 안 올리면 다른 테스트까지 429로 막힌다.
        properties = {
            "kraft.community.write-rate-limit-per-minute=2000",
            "kraft.security.rate-limit-per-minute=2000"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("커뮤니티 게시글·댓글 API 행위 검증")
class CommunityPostCommentApiTest {

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
    @DisplayName("익명 사용자도 게시글 목록·상세를 인증 없이 조회할 수 있다")
    void anonymousUser_canReadPostsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/community/posts"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("검색어의 LIKE 메타문자는 최신순 목록에서 리터럴로 취급된다")
    void searchLikeMetacharacters_areLiteralForLatestSort() throws Exception {
        createPost(owner, "정확 50%_sale! 행사", "검색 대상");
        createPost(owner, "정확 50AAAsale! 행사", "와일드카드라면 잘못 포함될 글");

        mockMvc.perform(get("/api/v1/community/posts")
                        .param("sort", "latest")
                        .param("query", "50%_sale!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].title").value("정확 50%_sale! 행사"));
    }

    @Test
    @DisplayName("KB-03/KB-11: 게시글 목록·상세·댓글 GET은 명시적으로 no-store를 응답하고 상세에는 ETag가 없다")
    void publicGetEndpoints_returnNoStoreWithoutStaleEtagCaching() throws Exception {
        long postId = createPost(owner, "제목", "내용");

        mockMvc.perform(get("/api/v1/community/posts"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        mockMvc.perform(get("/api/v1/community/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("ETag"));

        mockMvc.perform(get("/api/v1/community/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("인증되지 않은 요청은 게시글 작성이 거부된다")
    void unauthenticatedRequest_cannotCreatePost() throws Exception {
        mockMvc.perform(post("/api/v1/community/posts")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreatePostRequest("제목", "내용", "GENERAL", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("본인 게시글이 아니면 수정·삭제가 403으로 거부된다")
    void nonOwner_cannotUpdateOrDeletePost() throws Exception {
        long postId = createPost(owner, "제목", "내용");

        mockMvc.perform(put("/api/v1/community/posts/" + postId)
                        .with(asUser(other))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdatePostRequest("새 제목", "새 내용", 0L))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/community/posts/" + postId)
                        .param("expectedVersion", "0")
                        .with(asUser(other))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("게시글 삭제 시 버전이 다르면 409를 반환하고 게시글을 보존한다")
    void deletingPost_withStaleVersion_returnsConflictAndKeepsPost() throws Exception {
        long postId = createPost(owner, "제목", "내용");

        mockMvc.perform(delete("/api/v1/community/posts/" + postId)
                        .param("expectedVersion", "999")
                        .with(asUser(owner))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUNITY_POST_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/community/posts/" + postId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("작성자는 정상적으로 게시글을 수정하고 삭제할 수 있다")
    void owner_canUpdateAndDeletePostSuccessfully() throws Exception {
        long postId = createPost(owner, "제목", "내용");

        mockMvc.perform(put("/api/v1/community/posts/" + postId)
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdatePostRequest("새 제목", "새 내용", 0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 제목"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(delete("/api/v1/community/posts/" + postId)
                        .param("expectedVersion", "1")
                        .with(asUser(owner))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/community/posts/" + postId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CSRF 토큰 없이 보내는 쓰기 요청은 403 JSON으로 거부된다")
    void writeRequestWithoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/community/posts")
                        .with(asUser(owner))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreatePostRequest("제목", "내용", "GENERAL", null))))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.code").value("COMMUNITY_CSRF_REJECTED"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/v1/community/posts"));
    }

    @Test
    @DisplayName("본인 댓글이 아니면 삭제가 403으로 거부된다")
    void nonOwner_cannotDeleteComment() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        long commentId = createComment(owner, postId, "댓글", null);

        mockMvc.perform(delete("/api/v1/community/comments/" + commentId)
                        .with(asUser(other))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("삭제된(tombstone) 댓글에는 답글을 달 수 없다")
    void replyToDeletedComment_isRejected() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        long commentId = createComment(owner, postId, "댓글", null);

        mockMvc.perform(delete("/api/v1/community/comments/" + commentId)
                        .with(asUser(owner))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .with(asUser(other))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("답글", commentId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMUNITY_COMMENT_PARENT_DELETED"));
    }

    @Test
    @DisplayName("삭제된 댓글 응답에는 작성자 ID가 노출되지 않는다")
    void tombstonedComment_hidesOwnerId() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        long commentId = createComment(owner, postId, "댓글", null);

        mockMvc.perform(delete("/api/v1/community/comments/" + commentId)
                        .with(asUser(owner))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/community/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topLevel[0].ownerId").doesNotExist());
    }

    @Test
    @DisplayName("댓글 작성 응답은 목록 상 위치를 targetPage로 알려준다")
    void creatingComment_returnsTargetPage() throws Exception {
        long postId = createPost(owner, "제목", "내용");

        String body = mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("첫 댓글", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetPage").value(0))
                .andReturn().getResponse().getContentAsString();
        long topLevelId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .with(asUser(other))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("답글", topLevelId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetPage").value(0));
    }

    @Test
    @DisplayName("상위 댓글이 50개를 넘는 시점에 작성하면 targetPage가 다음 페이지를 가리킨다")
    void creatingComment_returnsSecondTargetPageAfter50Comments() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        for (int i = 0; i < 60; i++) {
            createComment(owner, postId, "댓글 " + i, null);
        }

        mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("61번째 댓글", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetPage").value(1));
    }

    @Test
    @DisplayName("상위 댓글이 50개를 초과하면 2페이지에서 51번째 댓글부터 보인다")
    void commentList_pagesBeyondFirst50TopLevelComments() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        for (int i = 0; i < 55; i++) {
            createComment(owner, postId, "댓글 " + i, null);
        }

        mockMvc.perform(get("/api/v1/community/posts/" + postId + "/comments").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTopLevelComments").value(55))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.topLevel.length()").value(5))
                .andExpect(jsonPath("$.topLevel[0].content").value("댓글 50"));
    }

    @Test
    @DisplayName("답글은 상위 댓글 페이지 집계에서 제외되고 상위 댓글에 중첩되어 내려온다")
    void commentList_nestsRepliesUnderTopLevelAndExcludesFromCount() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        long topLevelId = createComment(owner, postId, "상위 댓글", null);
        createComment(other, postId, "답글1", topLevelId);
        createComment(other, postId, "답글2", topLevelId);

        mockMvc.perform(get("/api/v1/community/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTopLevelComments").value(1))
                .andExpect(jsonPath("$.topLevel.length()").value(1))
                .andExpect(jsonPath("$.topLevel[0].replies.length()").value(2))
                .andExpect(jsonPath("$.topLevel[0].replies[0].content").value("답글1"))
                .andExpect(jsonPath("$.topLevel[0].replies[1].content").value("답글2"));
    }

    @Test
    @DisplayName("게시글 수정 시 버전이 다르면 409를 반환한다")
    void updatingPost_withStaleVersion_returnsConflict() throws Exception {
        long postId = createPost(owner, "제목", "내용");

        mockMvc.perform(put("/api/v1/community/posts/" + postId)
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdatePostRequest("새 제목", "새 내용", 999L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUNITY_POST_VERSION_CONFLICT"));
    }

    @Test
    @DisplayName("답글에는 답글을 달 수 없다")
    void replyToReply_isRejected() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        long parentCommentId = createComment(owner, postId, "부모 댓글", null);
        long replyId = createComment(other, postId, "답글", parentCommentId);

        mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("답글의 답글", replyId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMUNITY_COMMENT_REPLY_DEPTH_EXCEEDED"));
    }

    @Test
    @DisplayName("댓글을 삭제하면 행은 유지되고 내용·작성자만 마스킹된다")
    void deletingComment_tombstonesInsteadOfHardDelete() throws Exception {
        long postId = createPost(owner, "제목", "내용");
        long commentId = createComment(owner, postId, "삭제될 댓글", null);

        mockMvc.perform(delete("/api/v1/community/comments/" + commentId)
                        .with(asUser(owner))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/community/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTopLevelComments").value(1))
                .andExpect(jsonPath("$.topLevel[0].deleted").value(true))
                .andExpect(jsonPath("$.topLevel[0].content").value("삭제된 댓글입니다."));
    }

    @Test
    @DisplayName("게시글 생성 응답은 Location과 ETag 헤더를 포함한다")
    void creatingPost_returnsLocationAndETagHeaders() throws Exception {
        mockMvc.perform(post("/api/v1/community/posts")
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreatePostRequest("제목", "내용", "GENERAL", null))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/community/posts/")))
                .andExpect(header().string("ETag", "\"0\""));
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 조회하면 404를 반환한다")
    void detailOfMissingPost_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/community/posts/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMUNITY_POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("게시글 목록 size는 최대 50으로 클램프된다")
    void postList_clampsSizeTo50() throws Exception {
        mockMvc.perform(get("/api/v1/community/posts").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    @DisplayName("댓글 목록 size는 최대 100으로 클램프된다")
    void commentList_clampsSizeTo100() throws Exception {
        long postId = createPost(owner, "제목", "내용");

        mockMvc.perform(get("/api/v1/community/posts/" + postId + "/comments").param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("CSRF 토큰 없이 보내는 댓글 작성 요청은 403으로 거부된다")
    void creatingCommentWithoutCsrfToken_isForbidden() throws Exception {
        long postId = createPost(owner, "제목", "내용");

        mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .with(asUser(owner))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("댓글", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("다른 게시글의 댓글을 부모로 지정하면 400을 반환한다")
    void replyToParentFromDifferentPost_returns400() throws Exception {
        long postId1 = createPost(owner, "제목1", "내용1");
        long postId2 = createPost(owner, "제목2", "내용2");
        long commentIdOnPost1 = createComment(owner, postId1, "댓글", null);

        mockMvc.perform(post("/api/v1/community/posts/" + postId2 + "/comments")
                        .with(asUser(other))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("답글", commentIdOnPost1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMUNITY_COMMENT_PARENT_MISMATCH"));
    }

    @Test
    @DisplayName("댓글 작성 성공 응답은 전체 필드를 올바르게 채운다")
    void creatingComment_returnsFullResponseBody() throws Exception {
        long postId = createPost(owner, "제목", "내용");

        mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("첫 댓글", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.authorNickname").value(owner.getNickname()))
                .andExpect(jsonPath("$.content").value("첫 댓글"))
                .andExpect(jsonPath("$.deleted").value(false));
    }

    // 게시글 삭제 시 댓글 cascade 삭제는 DB의 ON DELETE CASCADE(V16)에 의존한다 — 테스트
    // 프로필은 Flyway 없이 Hibernate ddl-auto=create-drop으로 스키마를 생성해 FK 제약이
    // 재현되지 않으므로, 이 시나리오는 Testcontainers 기반 CommunityCommentCascadeDeleteTest에서 검증한다.

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

    private long createComment(CommunityUser author, long postId, String content, Long parentId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .with(asUser(author))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest(content, parentId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).isNotBlank();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private RequestPostProcessor asUser(CommunityUser user) {
        CommunityPrincipal principal = new CommunityPrincipal(user.getId(), user.getNickname());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(authentication);
    }
}
