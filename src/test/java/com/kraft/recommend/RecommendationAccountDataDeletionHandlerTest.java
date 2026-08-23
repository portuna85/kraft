package com.kraft.recommend;

import com.kraft.QueryCount;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TD-014: 계정 탈퇴 시 세트 수만큼 개별 delete를 반복하던 것을 벌크 DELETE로 대체한 뒤,
 * FK 순서(아이템 → 세트)와 트랜잭션 원자성을 실 MariaDB로 검증한다. H2는 Flyway가 정의한
 * FK 제약을 그대로 검증하지 못한다(스키마가 Hibernate DDL로 생성됨) — 실 DB가 필요하다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("추천 세트 계정 데이터 삭제 핸들러 테스트 (실 MariaDB)")
class RecommendationAccountDataDeletionHandlerTest {

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
    private RecommendationAccountDataDeletionHandler handler;

    @Autowired
    private RecommendationSetRepository recommendationSetRepository;

    @Autowired
    private RecommendationItemRepository recommendationItemRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private MeterRegistry meterRegistry;

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
    private final AtomicInteger providerIdSeq = new AtomicInteger();

    // deleteForAccount는 AccountDataDeletionHandler 계약대로 자체 트랜잭션을 열지 않고
    // 호출자(CommunityWithdrawalService.withdraw, @Transactional)의 트랜잭션에 얹혀
    // 실행된다. @Modifying 벌크 DELETE는 활성 트랜잭션 없이는 실행할 수 없으므로, 테스트도
    // 실제 호출 맥락과 동일하게 트랜잭션 안에서 호출해야 한다.
    private void deleteForAccountInTransaction(Long userId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> handler.deleteForAccount(userId));
    }

    @BeforeEach
    void cleanUp() {
        recommendationItemRepository.deleteAll();
        recommendationSetRepository.deleteAll();
        communityUserRepository.deleteAll();
    }

    // recommendation_sets.owner_user_id는 community_users(id)를 참조하는 FK다 — 실 계정
    // 행 없이는 세트를 귀속시킬 수 없다(H2였다면 이 FK 자체가 검증되지 않아 이 문제를
    // 놓쳤을 것 — 실 MariaDB를 쓰는 이유이기도 하다).
    private Long createUser() {
        CommunityUser user = communityUserRepository.save(new CommunityUser(
                "google", "provider-id-" + providerIdSeq.incrementAndGet(), "테스트유저", null, NOW));
        return user.getId();
    }

    /** 세트 하나 + 아이템 두 개를 실제로 저장하고, 저장된 세트에 ownerUserId를 귀속시킨다. */
    private RecommendationSet seedOwnedSet(Long ownerUserId) {
        RecommendationSet set = recommendationSetRepository.save(new RecommendationSet(
                "client-token-" + ownerUserId + "-" + providerIdSeq.incrementAndGet(), "random",
                "uniform-random-v1", 1100, "historical-first-prize-v1", null, null, NOW));
        set.claimTo(ownerUserId, NOW);
        recommendationSetRepository.save(set);

        recommendationItemRepository.saveAll(List.of(
                new RecommendationItem(set.getId(), 1, "1,2,3,4,5,6", 0b111111L, null, null),
                new RecommendationItem(set.getId(), 2, "7,8,9,10,11,12", 0b111111000000L, null, null)));
        return set;
    }

    @Test
    @DisplayName("해당 계정의 세트·아이템을 모두 지우고 다른 계정 데이터는 남긴다")
    void deleteForAccount_removesOwnedSetsAndItemsOnly() {
        Long owner = createUser();
        Long otherOwner = createUser();
        RecommendationSet ownedSet1 = seedOwnedSet(owner);
        RecommendationSet ownedSet2 = seedOwnedSet(owner);
        RecommendationSet otherUsersSet = seedOwnedSet(otherOwner);

        deleteForAccountInTransaction(owner);

        assertThat(recommendationSetRepository.findById(ownedSet1.getId())).isEmpty();
        assertThat(recommendationSetRepository.findById(ownedSet2.getId())).isEmpty();
        assertThat(recommendationItemRepository.findBySetIdOrderByPosition(ownedSet1.getId())).isEmpty();
        assertThat(recommendationItemRepository.findBySetIdOrderByPosition(ownedSet2.getId())).isEmpty();

        assertThat(recommendationSetRepository.findById(otherUsersSet.getId())).isPresent();
        assertThat(recommendationItemRepository.findBySetIdOrderByPosition(otherUsersSet.getId())).hasSize(2);
    }

    @Test
    @DisplayName("삭제 대상 세트가 없으면 아무 것도 하지 않는다")
    void deleteForAccount_noSets_isNoop() {
        Long owner = createUser();
        Long userWithNoSets = createUser();
        seedOwnedSet(owner);

        deleteForAccountInTransaction(userWithNoSets);

        assertThat(recommendationSetRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("아이템을 세트보다 먼저 지워 FK 위반 없이 끝난다")
    void deleteForAccount_deletesItemsBeforeSets_noForeignKeyViolation() {
        Long owner = createUser();
        seedOwnedSet(owner);
        seedOwnedSet(owner);

        // FK 위반 시 여기서 예외가 던져진다 — 던지지 않으면 통과.
        deleteForAccountInTransaction(owner);

        assertThat(recommendationItemRepository.count()).isZero();
        assertThat(recommendationSetRepository.count()).isZero();
    }

    @Test
    @DisplayName("호출자 트랜잭션이 이후 실패하면 삭제도 함께 롤백된다")
    void deleteForAccount_rollsBackWithCallerTransaction() {
        Long owner = createUser();
        RecommendationSet set = seedOwnedSet(owner);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            handler.deleteForAccount(owner);
            throw new RuntimeException("강제 롤백");
        })).hasMessage("강제 롤백");

        // 핸들러의 삭제 자체는 호출자 트랜잭션에 속한다(AccountDataDeletionHandler 계약) —
        // 호출자가 이후 실패해 트랜잭션 전체가 롤백되면 삭제도 원복돼야 한다.
        assertThat(recommendationSetRepository.findById(set.getId())).isPresent();
        assertThat(recommendationItemRepository.findBySetIdOrderByPosition(set.getId())).hasSize(2);
    }

    @Test
    @DisplayName("세트 수가 많아도 조회 1회와 벌크 삭제 2회로 끝나고 실행 시간을 기록한다")
    void deleteForAccount_manySets_usesConstantStatementCountAndRecordsDuration() {
        Long owner = createUser();
        for (int i = 0; i < 25; i++) {
            seedOwnedSet(owner);
        }
        long timerCountBefore = meterRegistry.get("kraft_recommendation_account_deletion_duration_seconds")
                .timer().count();

        // QueryCount(docs/improvement.md): 이 인라인 측정 코드가 원본이었다 — 공용 하네스로
        // 추출됐으니 여기서는 그 하네스를 쓴다.
        long statementCount = QueryCount.measure(entityManagerFactory, () -> deleteForAccountInTransaction(owner));

        assertThat(statementCount).isEqualTo(3L);
        assertThat(meterRegistry.get("kraft_recommendation_account_deletion_duration_seconds")
                .timer().count()).isEqualTo(timerCountBefore + 1);
    }
}
