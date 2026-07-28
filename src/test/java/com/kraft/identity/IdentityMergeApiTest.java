package com.kraft.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.Application;
import com.kraft.common.lotto.LottoNumberCodec;
import com.kraft.common.web.DeviceTokenSupport;
import com.kraft.community.auth.CommunityPrincipal;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import com.kraft.recommend.RecommendationSet;
import com.kraft.recommend.RecommendationSetRepository;
import com.kraft.saved.SavedNumber;
import com.kraft.saved.SavedNumberRepository;
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

// 의도적으로 @Transactional을 쓰지 않는다 — IdentityMergeService는 SavedNumberClientLock의
// REQUIRES_NEW 잠금 패턴(B2)을 재사용하는데, 테스트 전체를 감싸는 미완료 트랜잭션 안에서
// 같은 잠금 행을 두 번째로 건드리면(멱등 재시도·충돌 테스트가 claim을 두 번 호출) 그
// 트랜잭션이 잡고 있는 락 때문에 REQUIRES_NEW 커넥션이 타임아웃난다(Spring 테스트 트랜잭션과
// REQUIRES_NEW의 알려진 상충). 각 요청이 실제로 커밋되게 두고, 기기 토큰 값을 테스트마다
// 고유하게 생성해 테스트 간 데이터 격리를 유지한다.
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("로그인 계정 귀속 API 행위 검증")
class IdentityMergeApiTest {

    private final String rawDeviceToken = "device-token-" + System.nanoTime() + "-padding-to-length";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Autowired
    private SavedNumberRepository savedNumberRepository;

    @Autowired
    private RecommendationSetRepository recommendationSetRepository;

    @Autowired
    private DeviceTokenSupport deviceTokenSupport;

    @Autowired
    private LottoNumberCodec lottoNumberCodec;

    private String deviceTokenHash;
    private CommunityUser owner;
    private CommunityUser other;

    @BeforeEach
    void setUp() {
        deviceTokenHash = deviceTokenSupport.requireHashedToken(rawDeviceToken);
        owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "글쓴이", null, OffsetDateTime.now()));
        other = communityUserRepository.save(new CommunityUser(
                "google", "other-" + System.nanoTime(), "다른사람", null, OffsetDateTime.now()));
    }

    @Test
    @DisplayName("기기 토큰의 저장 번호·추천 이력을 계정으로 귀속하고 me 엔드포인트로 조회할 수 있다")
    void claimDevice_movesDataAndExposesViaMyEndpoints() throws Exception {
        savedNumberRepository.save(new SavedNumber(
                deviceTokenHash, lottoNumberCodec.toStorageValue(java.util.List.of(1, 2, 3, 4, 5, 6)),
                "즐겨찾기", "MANUAL", OffsetDateTime.now()));
        recommendationSetRepository.save(new RecommendationSet(
                deviceTokenHash, "random", "uniform-random-v1", 1189, null, null, OffsetDateTime.now()));

        mockMvc.perform(post("/api/v1/community/session/claim-device")
                        .with(asUser(owner))
                        .with(csrf())
                        .header("X-Device-Token", rawDeviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mergedSavedNumberCount").value(1))
                .andExpect(jsonPath("$.mergedRecommendationSetCount").value(1));

        mockMvc.perform(get("/api/v1/community/me/saved-numbers").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/community/me/recommendation-sets").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("같은 사용자가 재시도하면 멱등하게 0건 이동으로 성공한다")
    void claimDevice_retryBySameUser_isIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/community/session/claim-device")
                        .with(asUser(owner))
                        .with(csrf())
                        .header("X-Device-Token", rawDeviceToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/community/session/claim-device")
                        .with(asUser(owner))
                        .with(csrf())
                        .header("X-Device-Token", rawDeviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mergedSavedNumberCount").value(0));
    }

    @Test
    @DisplayName("다른 사용자가 같은 기기 토큰으로 귀속을 시도하면 409로 거부된다")
    void claimDevice_byDifferentUser_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/community/session/claim-device")
                        .with(asUser(owner))
                        .with(csrf())
                        .header("X-Device-Token", rawDeviceToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/community/session/claim-device")
                        .with(asUser(other))
                        .with(csrf())
                        .header("X-Device-Token", rawDeviceToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_ALREADY_CLAIMED"));
    }

    private RequestPostProcessor asUser(CommunityUser user) {
        CommunityPrincipal principal = new CommunityPrincipal(user.getId(), user.getNickname());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(authentication);
    }
}
