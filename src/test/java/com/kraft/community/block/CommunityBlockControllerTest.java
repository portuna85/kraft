package com.kraft.community.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.Application;
import com.kraft.community.auth.CommunityPrincipal;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
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

@SpringBootTest(
        classes = Application.class,
        // I-17 상한 테스트가 101건의 차단 쓰기를 빠르게 낸다 — 기본 write-rate-limit
        // (분당 20건)에 걸리지 않게 완화한다(CommunityPostCommentApiTest.java와 동일 이유).
        properties = {
            "kraft.community.write-rate-limit-per-minute=2000",
            "kraft.security.rate-limit-per-minute=2000"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
// I-15: CommunityBlockService.block()이 이제 Propagation.NOT_SUPPORTED다 — 재시도
// sleep 동안 커넥션을 붙잡지 않으려고 바깥 트랜잭션을 일부러 정지시킨다. 이 클래스에
// @Transactional을 붙이면(테스트당 롤백 패턴) setUp()의 INSERT가 테스트 트랜잭션
// 안에 커밋 안 된 채로만 남아, NOT_SUPPORTED로 별도 커넥션을 쓰는 block() 쪽에서는
// existsById가 그 사용자를 못 찾아 404가 난다. 대신 매 테스트가 nanoTime 접미사로
// 고유한 사용자를 만들어 테스트 간 격리를 보장한다(정리는 생략 — H2가 프로세스마다
// 새로 생성된다).
@DisplayName("커뮤니티 사용자 차단 API 행위 검증")
class CommunityBlockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CommunityUser blocker;
    private CommunityUser target;

    @BeforeEach
    void setUp() {
        blocker = communityUserRepository.save(new CommunityUser(
                "google", "blocker-" + System.nanoTime(), "차단하는사람", null, OffsetDateTime.now()));
        target = communityUserRepository.save(new CommunityUser(
                "google", "target-" + System.nanoTime(), "차단당하는사람", null, OffsetDateTime.now()));
    }

    @Test
    @DisplayName("인증되지 않은 요청은 차단 목록 조회가 거부된다")
    void unauthenticatedRequest_cannotListBlockedUsers() throws Exception {
        mockMvc.perform(get("/api/v1/community/me/blocked-users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("차단하면 차단 목록에 나타나고, 해제하면 사라진다")
    void block_thenUnblock_reflectsInBlockedUsersList() throws Exception {
        mockMvc.perform(put("/api/v1/community/users/" + target.getId() + "/block")
                        .with(asUser(blocker))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/community/me/blocked-users").with(asUser(blocker)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0]").value(target.getId()));

        mockMvc.perform(delete("/api/v1/community/users/" + target.getId() + "/block")
                        .with(asUser(blocker))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        String body = mockMvc.perform(get("/api/v1/community/me/blocked-users").with(asUser(blocker)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<?> ids = objectMapper.readValue(body, List.class);
        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("자기 자신은 차단할 수 없다")
    void cannotBlockSelf() throws Exception {
        mockMvc.perform(put("/api/v1/community/users/" + blocker.getId() + "/block")
                        .with(asUser(blocker))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMUNITY_SELF_BLOCK_NOT_ALLOWED"));
    }

    // 차단 upsert의 멱등성(동시 호출해도 행이 하나만 남는지)은 실제 유니크 제약이
    // 필요해 CommunityBlockConcurrencyTest(Testcontainers, 실 MariaDB)가 이미 검증한다 —
    // 이 클래스가 쓰는 H2 test 프로필은 Hibernate ddl-auto가 만든 스키마라 엔티티에
    // 명시하지 않은 복합 유니크 제약(uk_community_user_blocks_blocker_blocked)이
    // 재현되지 않는다(CommunityPostCommentApiTest.java의 FK 재현 불가 주석과 같은 종류의
    // 간극). 여기서는 컨트롤러 계층(인증·상태 코드·응답 바디)만 검증한다.

    // I-17: 차단 목록 응답이 무제한이 아니라 최근 100개로 상한이 걸려야 한다
    // (/me/interactions의 postIds 상한과 같은 값). 101명을 차단해 상한이 실제로
    // 걸리는지 확인한다 — CommunityUserBlockRepository.findTop100... 자체는
    // findByBlockerUserId(무제한)와 별개 메서드라 이 테스트가 없으면 컨트롤러가
    // 실수로 무제한 메서드를 계속 쓰는 회귀를 못 잡는다.
    @Test
    @DisplayName("차단한 사용자가 100명을 넘으면 응답이 100개로 상한이 걸린다")
    void blockedUsersList_isCappedAt100() throws Exception {
        for (int i = 0; i < 101; i++) {
            CommunityUser victim = communityUserRepository.save(new CommunityUser(
                    "google", "victim-" + i + "-" + System.nanoTime(), "차단대상" + i, null, OffsetDateTime.now()));
            mockMvc.perform(put("/api/v1/community/users/" + victim.getId() + "/block")
                            .with(asUser(blocker))
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        String body = mockMvc.perform(get("/api/v1/community/me/blocked-users").with(asUser(blocker)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<?> ids = objectMapper.readValue(body, List.class);
        assertThat(ids).hasSize(100);
    }

    private RequestPostProcessor asUser(CommunityUser user) {
        CommunityPrincipal principal = new CommunityPrincipal(user.getId(), user.getNickname());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(authentication);
    }
}
