package com.kraft;

import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.post.PostCategory;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import com.kraft.winningnumber.WinningNumberRepository;
import com.kraft.winningnumber.WinningNumberTestFactory;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-PERF-01(docs/improvement.md): "홈 1요청 = 6쿼리" 발견을 고정한 characterization 테스트였다.
 *
 * <p>{@code /api/v1/home}은 {@code CommunityPostRepository#findWeeklyPopularPublished}(M-16: POW/
 * TIMESTAMPDIFF/UTC_TIMESTAMP(6)를 쓰는 MariaDB 전용 native SQL)을 거치므로 기본 H2
 * {@code create-drop} 프로파일에서는 "Function POW not found"로 500이 난다
 * (QA-BE-01/docs/improvement.md가 지적하는 바로 그 사각지대) — 그래서
 * {@link BaseApiIntegrationTest}가 아니라 {@link com.kraft.community.post.CommunityPostRepositoryNativeQueryTest}와
 * 같은 실 MariaDB Testcontainers 위에서 돈다.
 *
 * <p><b>[완료 2026-08-23] BE-CACHE-01 + BE-PERF-01 구현 후 실측 3이다.</b> 원래 이 클래스는
 * "게시글 6개 이상일 때 5"를 단정해 발견을 고정하는 green characterization 테스트였다(게시글
 * 1개일 때는 3 — {@code PageableExecutionUtils}가 {@code offset == 0 && content.size() < pageSize}일
 * 때 count 쿼리를 생략하기 때문). 구현 PR에서 (1) {@code WinningNumberQueryService.findLatest()}에
 * {@code @Cacheable(ROUNDS_LATEST)}를 붙이고 {@code getFreshness()}를
 * {@code findLatest().map(this::freshnessOf)}로 재작성해 회차 중복 조회를 없앴고,
 * (2) {@code CommunityPostRepository}에 {@code findLatestPublished}/{@code findWeeklyPopularPublished}
 * (List 반환, count 쿼리 없음) 형제 메서드를 추가해 홈이 더 이상 안 쓰는 {@code getTotalElements()}를
 * 위한 count 쿼리를 실행하지 않는다. 그 결과 게시글 수와 무관하게 항상 3(회차 1 + 목록 2)이다 —
 * 데이터 규모별로 갈리던 3~5 구간이 사라졌다.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DisplayName("홈 API 쿼리 수 characterization 테스트 (실 MariaDB)")
class HomeApiQueryCountCharacterizationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private CommunityPostRepository communityPostRepository;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Autowired
    private WinningNumberRepository winningNumberRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("회차·게시글이 둘 다 있어도 /api/v1/home 1회 호출은 3개의 prepared statement만 실행한다")
    void homeEndpoint_executesThreeStatements() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));

        winningNumberRepository.save(WinningNumberTestFactory.create(
                1, LocalDate.of(2026, 6, 20), 5, 12, 18, 27, 36, 44, 7,
                1_000_000_000L, 0L, 0, 0L, 0L, now));

        CommunityUser owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "작성자", null, now));
        // findLatestPublished/findWeeklyPopularPublished는 count 쿼리 자체가 없는 List 반환
        // 메서드라 게시글 수와 무관하게 항상 3이다 — 페이지 크기(5)를 넘기는 이 개수는 이제
        // count-skip 최적화 여부를 가르기 위함이 아니라, 회귀 시(누군가 실수로 Page 반환
        // 메서드로 되돌리면) 곧바로 5로 튀어 이 테스트가 잡아내도록 남겨둔다.
        for (int i = 0; i < 6; i++) {
            communityPostRepository.save(new CommunityPost(
                    owner.getId(), "작성자", "제목" + i, "내용", PostCategory.GENERAL, null, now, now));
        }

        long statementCount = QueryCount.measure(entityManagerFactory, () -> {
            try {
                mockMvc.perform(get("/api/v1/home")).andExpect(status().isOk());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(statementCount).isEqualTo(3L);
    }
}
