package com.kraft.recommend;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

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

        List<RecommendationSet> all =
                recommendationSetRepository.findByClientTokenHashOrderByCreatedAtDescIdDesc("token-a");

        assertThat(all.stream().map(RecommendationSet::getId).toList()).containsExactly(id2, id1);
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
