package com.kraft.community.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.post.PostCategory;
import com.kraft.recommend.RecommendationSet;
import com.kraft.recommend.RecommendationSetRepository;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BE-02(docs/improvement.md): 세트는 IdentityMergeService.claim()으로 소유권이 넘어갈 수
 * 있는데, 탈퇴 처리는 탈퇴자 "본인 소유" 게시글의 recommendation_set_id만 끊고
 * RecommendationAccountDataDeletionHandler는 탈퇴자가 소유한 세트를 무조건 삭제한다.
 * recommendation_set_id는 ON DELETE RESTRICT(V21)이므로, 다른 계정 게시글이 그 세트를
 * 여전히 참조하면 삭제가 DataIntegrityViolationException(500)이 됐다. H2는 이 컬럼에
 * JPA 레벨 FK가 없어(CommunityPost.recommendationSetId는 평범한 Long) 이 제약을 재현하지
 * 못한다 — 실 MariaDB(Flyway V21)가 필요하다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("탈퇴 시 다른 계정이 참조하는 추천 세트 FK 처리 테스트 (실 MariaDB)")
class CommunityWithdrawalRecommendationSetFkTest {

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.7")
            .withDatabaseName("kraft_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private CommunityWithdrawalService communityWithdrawalService;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Autowired
    private CommunityPostRepository communityPostRepository;

    @Autowired
    private RecommendationSetRepository recommendationSetRepository;

    private static final OffsetDateTime NOW = OffsetDateTime.now();
    private final AtomicInteger seq = new AtomicInteger();

    @BeforeEach
    void cleanUp() {
        communityPostRepository.deleteAll();
        recommendationSetRepository.deleteAll();
        communityUserRepository.deleteAll();
    }

    private CommunityUser createUser() {
        return communityUserRepository.save(new CommunityUser(
                "google", "provider-" + seq.incrementAndGet(), "user-" + seq.get(), null, NOW));
    }

    @Test
    @DisplayName("A가 첨부한 세트를 B가 claim으로 소유하게 된 뒤 B가 탈퇴해도 " +
            "A의 게시글이 FK 위반 없이 유지되고 첨부만 끊긴다")
    void withdraw_withSetReferencedByAnotherAccountsPost_detachesInsteadOfViolatingForeignKey() {
        CommunityUser postAuthor = createUser();
        CommunityUser setOwnerWithdrawing = createUser();

        // B(탈퇴 예정)가 소유한 세트. claim()으로 소유권이 넘어간 세트든 로그인 상태로 바로
        // 만든 세트든, DB에 남는 상태(ownerUserId만 있고 clientTokenHash는 없음)는 동일하다
        // — 이 테스트가 재현하려는 FK 문제는 그 상태 자체에서 비롯되므로 어느 경로로
        // 도달했는지는 상관없다.
        RecommendationSet set = recommendationSetRepository.save(new RecommendationSet(
                setOwnerWithdrawing.getId(), "random",
                "uniform-random-v1", 1100, "historical-first-prize-v1", null, null, NOW));

        // A(탈퇴하지 않음)의 게시글이 B 소유 세트를 첨부하고 있다.
        CommunityPost postByOtherAuthor = communityPostRepository.save(new CommunityPost(
                postAuthor.getId(), "author", "제목", "내용", PostCategory.GENERAL, set.getId(), NOW, NOW));

        assertThatCode(() -> communityWithdrawalService.withdraw(setOwnerWithdrawing.getId()))
                .doesNotThrowAnyException();

        assertThat(communityUserRepository.findById(setOwnerWithdrawing.getId())).isEmpty();
        assertThat(recommendationSetRepository.findById(set.getId())).isEmpty();

        CommunityPost reloaded = communityPostRepository.findById(postByOtherAuthor.getId()).orElseThrow();
        assertThat(reloaded.getOwnerId()).isEqualTo(postAuthor.getId());
        assertThat(reloaded.getRecommendationSetId()).isNull();
    }
}
