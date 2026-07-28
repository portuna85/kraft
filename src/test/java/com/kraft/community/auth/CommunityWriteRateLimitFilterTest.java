package com.kraft.community.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.Application;
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

@SpringBootTest(
        classes = Application.class,
        properties = "kraft.community.write-rate-limit-per-minute=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("커뮤니티 쓰기 rate limit 필터")
class CommunityWriteRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CommunityUser owner;

    @BeforeEach
    void setUp() {
        owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "글쓴이", null, OffsetDateTime.now()));
    }

    @Test
    @DisplayName("한도를 초과한 쓰기 요청은 ApiErrorResponse 형태의 429 JSON을 반환한다")
    void writeRateLimitExceeded_returnsApiErrorResponseJson() throws Exception {
        mockMvc.perform(post("/api/v1/community/posts")
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreatePostRequest("제목1", "내용1", "GENERAL", null))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/community/posts")
                        .with(asUser(owner))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreatePostRequest("제목2", "내용2", "GENERAL", null))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("COMMUNITY_WRITE_RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.path").value("/api/v1/community/posts"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private RequestPostProcessor asUser(CommunityUser user) {
        CommunityPrincipal principal = new CommunityPrincipal(user.getId(), user.getNickname());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(authentication);
    }
}
