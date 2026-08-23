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
 * BE-PERF-01(docs/improvement.md): "홈 1요청 = 6쿼리" 발견을 고정하는 characterization 테스트다.
 *
 * <p>{@code /api/v1/home}은 {@code CommunityPostRepository#findWeeklyPopular}(M-16: POW/
 * TIMESTAMPDIFF/UTC_TIMESTAMP(6)를 쓰는 MariaDB 전용 native SQL)을 거치므로 기본 H2
 * {@code create-drop} 프로파일에서는 "Function POW not found"로 500이 난다
 * (QA-BE-01/docs/improvement.md가 지적하는 바로 그 사각지대) — 그래서
 * {@link BaseApiIntegrationTest}가 아니라 {@link com.kraft.community.post.CommunityPostRepositoryNativeQueryTest}와
 * 같은 실 MariaDB Testcontainers 위에서 돈다.
 *
 * <p><b>실측치는 5다, 6이 아니다.</b> 게시글 1개(페이지 크기 5 미만)로 처음 측정했을 때는
 * 3이 나왔다 — Spring Data JPA의 {@code PageableExecutionUtils}가
 * {@code offset == 0 && content.size() < pageSize}일 때 count 쿼리 자체를 생략하기
 * 때문이다. 그 최적화가 꺼지도록 게시글을 페이지 크기(5) 이상으로 채워 측정한 값이 이
 * 클래스가 단정하는 5다 — 문서(BE-PERF-01)가 근거로 든 "6쿼리"는 findLatest()/getFreshness()가
 * 독립 호출이라는 사실과 Page의 버려지는 count 2건을 합친 이론값이며, 실측(이 테스트)은
 * 시나리오에 따라 3~5로 갈린다. **이 격차 자체가 발견이다** — 실제 낭비가 코드 리뷰만으로
 * 추정한 것보다 데이터 규모에 더 민감하게 반응한다.
 *
 * <p>이 저장소 관행은 보통 red부터 시작하지만(수정하려는 결함을 먼저 실패하는 테스트로
 * 남긴다), 이 테스트는 그 반대다 — **측정 하네스** 자체를 검증하는 것이 목적이라 지금은
 * 현재 수치를 그대로 단정해 green으로 남긴다. BE-PERF-01 구현 PR에서 이 숫자를 낮추는 것이
 * 그 PR의 증거가 된다(이 클래스의 assertion을 고치고 PR에 근거로 링크한다).
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
    @DisplayName("[BE-PERF-01 미구현] 회차·게시글이 둘 다 있으면 /api/v1/home 1회 호출이 5개의 prepared statement를 실행한다")
    void homeEndpoint_currentlyExecutesFiveStatements() {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));

        winningNumberRepository.save(WinningNumberTestFactory.create(
                1, LocalDate.of(2026, 6, 20), 5, 12, 18, 27, 36, 44, 7,
                1_000_000_000L, 0L, 0, 0L, 0L, now));

        CommunityUser owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "작성자", null, now));
        // 페이지 크기(5)를 넘겨야 Spring Data가 count 쿼리를 생략하지 않는다 — 그래야
        // 문서가 말하는 "버려지는 COUNT" 두 건이 실제로 발생한다.
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

        assertThat(statementCount).isEqualTo(5L);
    }
}
