package com.kraft;

import com.kraft.recommend.RecommendationItem;
import com.kraft.recommend.RecommendationItemRepository;
import com.kraft.recommend.RecommendationSet;
import com.kraft.recommend.RecommendationSetRepository;
import com.kraft.winningnumber.WinningNumberRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway 마이그레이션이 실제 MariaDB에서 오류 없이 적용되고,
 * Hibernate ddl-auto=validate 가 엔티티 매핑과 스키마가 일치함을 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("데이터베이스 마이그레이션 및 스키마 검증 테스트")
class FlywayMigrationTest {

    @Autowired
    private WinningNumberRepository winningNumberRepository;

    @Autowired
    private RecommendationSetRepository recommendationSetRepository;

    @Autowired
    private RecommendationItemRepository recommendationItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.7")
            .withDatabaseName("kraft_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        Flyway.configure()
                .dataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())
                .load()
                .migrate();
        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Test
    @DisplayName("마이그레이션이 성공적으로 적용되고 엔티티와 스키마가 일치한다")
    void migrationsApplySuccessfully_andSchemaMatchesEntities() {
        // 컨텍스트가 로드되면 테스트 통과:
        // 1. 모든 Flyway 마이그레이션이 MariaDB에서 오류 없이 적용됨
        // 2. Hibernate ddl-auto=validate 가 엔티티 매핑과 스키마 일치를 확인함
    }

    @Test
    @DisplayName("쿼리 계획으로 채택한 신고 대상·운영 로그 복합 인덱스가 생성된다")
    void queryPlanIndexes_arePresent() {
        assertThat(indexExists("community_reports", "idx_community_reports_target")).isTrue();
        assertThat(indexExists("winning_number_operation_logs", "idx_operation_logs_type_status_created")).isTrue();
        assertThat(indexExists("winning_number_operation_logs", "idx_operation_logs_type_status")).isFalse();
    }

    @Test
    @DisplayName("DB-IDX-01/DB-IDX-02/DB-REC-01: V35 커뮤니티·추천 쿼리 계획 인덱스가 생성된다")
    void communityQueryPlanIndexes_arePresent() {
        assertThat(indexExists("community_posts", "idx_community_posts_status_created")).isTrue();
        assertThat(indexExists("community_comments", "idx_community_comments_post_parent_created")).isTrue();
        assertThat(indexExists("recommendation_sets", "idx_recommendation_sets_owner_created")).isTrue();
        // DB-REC-01: 새 복합 인덱스가 FK owner_user_id 요구를 대신 만족하면서, V33이 FK
        // 제약과 함께 자동 생성했던 단일 컬럼 인덱스는 MariaDB가 알아서 제거한다(로컬에서
        // information_schema.statistics로 실측 확인 — 명시적 DROP INDEX가 불필요하고
        // 시도하면 오히려 에러가 난다).
        assertThat(indexExists("recommendation_sets", "fk_recommendation_sets_owner")).isFalse();
    }

    @Test
    @DisplayName("recommendation item trigger rejects historical combinations but permits a later winning combination")
    void historicalRecommendationTrigger_respectsHistoryThroughRound() {
        winningNumberRepository.deleteAll();
        recommendationItemRepository.deleteAll();
        recommendationSetRepository.deleteAll();

        winningNumberRepository.save(com.kraft.winningnumber.WinningNumberTestFactory.create(1, LocalDate.of(2026, 1, 3),
                1, 2, 3, 4, 5, 6, 7, 1L, 0L, 0, 0L, 0L, OffsetDateTime.now()));
        winningNumberRepository.save(com.kraft.winningnumber.WinningNumberTestFactory.create(2, LocalDate.of(2026, 1, 10),
                7, 8, 9, 10, 11, 12, 13, 1L, 0L, 0, 0L, 0L, OffsetDateTime.now()));

        RecommendationSet throughRoundOne = recommendationSetRepository.save(new RecommendationSet(
                "test-token-1", "random", "uniform-random-v1", 1,
                "historical-first-prize-v1", null, null, OffsetDateTime.now()));
        RecommendationItem laterWinner = recommendationItemRepository.saveAndFlush(new RecommendationItem(
                throughRoundOne.getId(), 1, "7,8,9,10,11,12", maskOf(7, 8, 9, 10, 11, 12), null, null));
        assertThat(laterWinner.getId()).isNotNull();

        RecommendationSet throughRoundTwo = recommendationSetRepository.save(new RecommendationSet(
                "test-token-2", "random", "uniform-random-v1", 2,
                "historical-first-prize-v1", null, null, OffsetDateTime.now()));
        assertThatThrownBy(() -> recommendationItemRepository.saveAndFlush(new RecommendationItem(
                throughRoundTwo.getId(), 1, "1,2,3,4,5,6", maskOf(1, 2, 3, 4, 5, 6), null, null)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("historical first-prize combination cannot be persisted");
    }

    @Test
    @DisplayName("V29 이전 INSERT도 마스크를 채우고 역사 조합 방어선을 유지한다")
    void legacyInsertWithoutCombinationMask_remainsCompatibleAndProtected() {
        recommendationItemRepository.deleteAll();
        recommendationSetRepository.deleteAll();
        winningNumberRepository.deleteAll();

        jdbcTemplate.update("""
                INSERT INTO winning_numbers (
                    round_no, draw_date, n1, n2, n3, n4, n5, n6, bonus_number,
                    first_prize_amount, second_prize, second_winners, total_sales,
                    first_accum_amount, version, created_at
                ) VALUES (1, '2026-01-03', 1, 2, 3, 4, 5, 6, 7, 1, 0, 0, 0, 0, 0, NOW(6))
                """);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT combination_mask FROM winning_numbers WHERE round_no = 1", Long.class))
                .isEqualTo(maskOf(1, 2, 3, 4, 5, 6));

        jdbcTemplate.update("""
                INSERT INTO recommendation_sets (
                    client_token_hash, strategy, algorithm_version, history_through_round,
                    locked_numbers, excluded_numbers, created_at
                ) VALUES (?, 'random', 'uniform-random-v1', 1, NULL, NULL, NOW(6))
                """, "legacy-token-hash");
        Long setId = jdbcTemplate.queryForObject(
                "SELECT id FROM recommendation_sets WHERE client_token_hash = ?", Long.class,
                "legacy-token-hash");

        jdbcTemplate.update("""
                INSERT INTO recommendation_items (set_id, position, numbers, score, explanation_codes)
                VALUES (?, 1, '7,8,9,10,11,12', NULL, NULL)
                """, setId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT combination_mask FROM recommendation_items WHERE set_id = ? AND position = 1",
                Long.class, setId)).isEqualTo(maskOf(7, 8, 9, 10, 11, 12));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO recommendation_items (set_id, position, numbers, score, explanation_codes)
                VALUES (?, 2, '1,2,3,4,5,6', NULL, NULL)
                """, setId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("historical first-prize combination cannot be persisted");
    }

    // --- M-04: 소유권 XOR·FK 제약 ---

    @Test
    @DisplayName("M-04: saved_numbers는 client_token_hash·owner_user_id가 둘 다 NULL이면 거부한다")
    void savedNumbers_bothOwnershipColumnsNull_isRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO saved_numbers (client_token_hash, owner_user_id, numbers, source, created_at)
                VALUES (NULL, NULL, '1,2,3,4,5,6', 'MANUAL', NOW(6))
                """))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("M-04: saved_numbers는 client_token_hash·owner_user_id가 둘 다 값이 있으면 거부한다")
    void savedNumbers_bothOwnershipColumnsSet_isRejected() {
        Long ownerId = insertCommunityUser("saved-xor-both-" + System.nanoTime());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO saved_numbers (client_token_hash, owner_user_id, numbers, source, created_at)
                VALUES (?, ?, '1,2,3,4,5,6', 'MANUAL', NOW(6))
                """, "some-token-hash-0000000000000000000000000000000000000000000000", ownerId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("M-04: recommendation_sets는 client_token_hash·owner_user_id가 둘 다 NULL이면 거부한다")
    void recommendationSets_bothOwnershipColumnsNull_isRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO recommendation_sets (
                    client_token_hash, owner_user_id, strategy, algorithm_version, history_through_round,
                    locked_numbers, excluded_numbers, created_at
                ) VALUES (NULL, NULL, 'random', 'uniform-random-v1', 1, NULL, NULL, NOW(6))
                """))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("M-04: recommendation_sets는 client_token_hash·owner_user_id가 둘 다 값이 있으면 거부한다")
    void recommendationSets_bothOwnershipColumnsSet_isRejected() {
        Long ownerId = insertCommunityUser("rec-xor-both-" + System.nanoTime());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO recommendation_sets (
                    client_token_hash, owner_user_id, strategy, algorithm_version, history_through_round,
                    locked_numbers, excluded_numbers, created_at
                ) VALUES (?, ?, 'random', 'uniform-random-v1', 1, NULL, NULL, NOW(6))
                """, "some-token-hash-0000000000000000000000000000000000000000000000", ownerId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("M-04: recommendation_sets는 존재하지 않는 계정을 가리키는 owner_user_id를 거부한다")
    void recommendationSets_orphanOwner_isRejectedByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO recommendation_sets (
                    client_token_hash, owner_user_id, strategy, algorithm_version, history_through_round,
                    locked_numbers, excluded_numbers, created_at
                ) VALUES (NULL, 999999999, 'random', 'uniform-random-v1', 1, NULL, NULL, NOW(6))
                """))
                .isInstanceOf(DataAccessException.class);
    }

    private Long insertCommunityUser(String providerId) {
        jdbcTemplate.update("""
                INSERT INTO community_users (provider, provider_id, nickname, profile_image_url, created_at)
                VALUES ('google', ?, '테스터', NULL, NOW(6))
                """, providerId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM community_users WHERE provider_id = ?", Long.class, providerId);
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """, Integer.class, tableName, indexName);
        return count != null && count > 0;
    }

    private static long maskOf(int... numbers) {
        long mask = 0L;
        for (int number : numbers) {
            mask |= 1L << (number - 1);
        }
        return mask;
    }
}
