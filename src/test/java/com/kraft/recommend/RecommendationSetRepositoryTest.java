package com.kraft.recommend;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("추천 세트 이력 정렬 테스트")
class RecommendationSetRepositoryTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private RecommendationSetRepository recommendationSetRepository;

    @BeforeEach
    void setUp() {
        recommendationSetRepository.deleteAll();
    }

    @Test
    @DisplayName("DB-REC-01: owner 목록은 동일 createdAt에서도 id 내림차순으로 결정적이다")
    void findByOwnerUserIdOrderByCreatedAtDescIdDesc_breaksTiesById() {
        OffsetDateTime sameInstant = OffsetDateTime.now(Clock.system(KST));
        Long id1 = save(owner(1L, sameInstant)).getId();
        Long id2 = save(owner(1L, sameInstant)).getId();
        Long id3 = save(owner(1L, sameInstant)).getId();

        Page<RecommendationSet> page = recommendationSetRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(
                1L, PageRequest.of(0, 10, Sort.unsorted()));

        assertThat(page.getContent().stream().map(RecommendationSet::getId).toList())
                .containsExactly(id3, id2, id1);
    }

    @Test
    @DisplayName("DB-REC-01: 기기 목록은 동일 createdAt에서도 id 내림차순으로 결정적이다")
    void findByClientTokenHashOrderByCreatedAtDescIdDesc_breaksTiesById() {
        OffsetDateTime sameInstant = OffsetDateTime.now(Clock.system(KST));
        Long id1 = save(device("token-a", sameInstant)).getId();
        Long id2 = save(device("token-a", sameInstant)).getId();

        Page<RecommendationSet> page = recommendationSetRepository.findByClientTokenHashOrderByCreatedAtDescIdDesc(
                "token-a", PageRequest.of(0, 10, Sort.unsorted()));

        assertThat(page.getContent().stream().map(RecommendationSet::getId).toList())
                .containsExactly(id2, id1);
    }

    @Test
    @Transactional
    // @Modifying 벌크 UPDATE는 활성 트랜잭션 없이는 실행할 수 없다
    // (CommunityCommentRepositoryTest와 같은 이유) — 이 테스트만 트랜잭션 안에서 돈다.
    @DisplayName("DATA-REC-01: 벌크 UPDATE로 해당 토큰의 세트만 계정 귀속하고 다른 토큰은 남긴다")
    void claimAllByClientTokenHash_movesOnlyMatchingTokenSets() {
        OffsetDateTime now = OffsetDateTime.now(Clock.system(KST));
        Long claimedId1 = save(device("token-a", now)).getId();
        Long claimedId2 = save(device("token-a", now)).getId();
        Long untouchedId = save(device("token-b", now)).getId();
        // DB 컬럼이 나노초 전체를 보존하지 못해(H2 test 스키마는 마이크로초 정밀도) 값을
        // 그대로 비교하면 어긋난다 — 저장 정밀도에 맞춰 미리 자른다.
        OffsetDateTime claimedAt = now.plusMinutes(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        int moved = recommendationSetRepository.claimAllByClientTokenHash("token-a", 42L, claimedAt);
        // clearAutomatically=true라 findById 등은 이미 DB 최신값을 본다 — 여기서는 그 계약을
        // 재확인할 뿐, 정말 필요한 clear()는 레포지토리 쪽 어노테이션이 이미 하고 있다.

        assertThat(moved).isEqualTo(2);
        RecommendationSet claimed1 = recommendationSetRepository.findById(claimedId1).orElseThrow();
        assertThat(claimed1.getOwnerUserId()).isEqualTo(42L);
        assertThat(claimed1.getClientTokenHash()).isNull();
        assertThat(claimed1.getClaimedAt()).isEqualTo(claimedAt);
        RecommendationSet claimed2 = recommendationSetRepository.findById(claimedId2).orElseThrow();
        assertThat(claimed2.getOwnerUserId()).isEqualTo(42L);

        RecommendationSet untouched = recommendationSetRepository.findById(untouchedId).orElseThrow();
        assertThat(untouched.getOwnerUserId()).isNull();
        assertThat(untouched.getClientTokenHash()).isEqualTo("token-b");
    }

    private RecommendationSet save(RecommendationSet set) {
        return recommendationSetRepository.save(set);
    }

    private static RecommendationSet owner(Long ownerUserId, OffsetDateTime createdAt) {
        return new RecommendationSet(ownerUserId, "RANDOM", "v1", 1200, "legacy-unverified",
                null, null, createdAt);
    }

    private static RecommendationSet device(String clientTokenHash, OffsetDateTime createdAt) {
        return new RecommendationSet(clientTokenHash, "RANDOM", "v1", 1200, "legacy-unverified",
                null, null, createdAt);
    }
}
