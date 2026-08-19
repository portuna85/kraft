package com.kraft;

import com.kraft.community.auth.CommunityPrincipal;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KF-01(docs/improvement.md) — 로그인 계정으로 추천을 생성하면 계정 이력에 즉시
 * 나타나는지 검증한다. {@code /api/v1/numbers/recommend}(익명)와 대비되는
 * {@code /api/v1/community/me/recommendation-sets}(계정, 인증 필요) 쓰기 경로.
 */
@DisplayName("계정 소유 추천 생성 API 행위 검증")
class MyLibraryRecommendApiTest extends BaseApiIntegrationTest {

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Test
    @DisplayName("로그인 상태에서 생성한 조합이 계정 소유로 저장되고 계정 이력 조회에 즉시 나타난다")
    void recommend_asLoggedInUser_persistsForOwnerAndAppearsInHistory() throws Exception {
        CommunityUser owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "글쓴이", null, OffsetDateTime.now()));

        mockMvc.perform(post("/api/v1/community/me/recommendation-sets")
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"count":1,"reduceSharedWinnerRisk":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setId").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.recommendations", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(get("/api/v1/community/me/recommendation-sets").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("인증 없이 호출하면 거부된다 — 익명 사용자는 이 경로로 생성할 수 없다")
    void recommend_withoutAuthentication_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/community/me/recommendation-sets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"count":1}
                                """))
                .andExpect(status().is4xxClientError());
    }

    private RequestPostProcessor asUser(CommunityUser user) {
        CommunityPrincipal principal = new CommunityPrincipal(user.getId(), user.getNickname());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(authentication);
    }
}
