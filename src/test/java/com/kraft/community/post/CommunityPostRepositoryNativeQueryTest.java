package com.kraft.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * M-16: {@link CommunityPostRepository#findWeeklyPopular}은 POW/TIMESTAMPDIFF/UTC_TIMESTAMP(6)를
 * 쓰는 MariaDB 전용 네이티브 SQL이다. 대부분의 @SpringBootTest는 H2 create-drop을 쓰기 때문에
 * 이 쿼리는 그동안 CommunityPostRepositoryConcurrencyTest의 LIKE 이스케이프 테스트에서만
 * 간접적으로(정렬 결과의 순위 계산 자체는 검증하지 않은 채) 실행되고 있었다. 여기서는 점수
 * 공식(좋아요*3 + 댓글*2 + min(조회,1000)/20, 최신도로 나눔)과 7일 컷오프를 실제 MariaDB로
 * 직접 검증한다.
 *
 * <p>{@code @Transactional}은 테스트 메서드마다 롤백해 컨테이너(클래스 레벨로 공유)는
 * 재사용하면서도 이전 테스트가 심은 게시글이 findWeeklyPopular의 무필터 결과에 섞여 들지
 * 않게 한다 — 처음에는 이게 빠져 있어 실제 CI(Docker 사용 가능)에서 recencyBreaksTieViaTimeDecay가
 * 심은 "최근 글"이 ordersByComputedPopularityScore의 결과에 leak되어 실패했다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DisplayName("주간 인기글 네이티브 쿼리 테스트 (실 MariaDB)")
class CommunityPostRepositoryNativeQueryTest {

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
    private CommunityPostRepository communityPostRepository;

    @Autowired
    private CommunityPostMetricsRepository communityPostMetricsRepository;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    private Long ownerId;

    @BeforeEach
    void setUp() {
        CommunityUser owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "글쓴이", null, OffsetDateTime.now()));
        ownerId = owner.getId();
    }

    @Test
    @DisplayName("좋아요·댓글·조회수가 높을수록 상위로 정렬된다")
    void ordersByComputedPopularityScore() {
        CommunityPost lowEngagement = createPost("낮은 인기", OffsetDateTime.now().minusHours(1));
        CommunityPost highEngagement = createPost("높은 인기", OffsetDateTime.now().minusHours(1));

        bumpLikes(highEngagement.getId(), 10);
        bumpComments(highEngagement.getId(), 5);

        List<String> titles = communityPostRepository
                .findWeeklyPopular(null, null, OffsetDateTime.now().minusDays(7), PageRequest.of(0, 10))
                .map(CommunityPost::getTitle)
                .getContent();

        assertThat(titles).containsExactly("높은 인기", "낮은 인기");
    }

    @Test
    @DisplayName("같은 점수라면 최신 글일수록(경과 시간이 짧을수록) 상위로 정렬된다")
    void recencyBreaksTieViaTimeDecay() {
        CommunityPost older = createPost("오래된 글", OffsetDateTime.now().minusDays(6));
        CommunityPost newer = createPost("최근 글", OffsetDateTime.now().minusHours(1));

        bumpLikes(older.getId(), 3);
        bumpLikes(newer.getId(), 3);

        List<String> titles = communityPostRepository
                .findWeeklyPopular(null, null, OffsetDateTime.now().minusDays(7), PageRequest.of(0, 10))
                .map(CommunityPost::getTitle)
                .getContent();

        assertThat(titles).containsExactly("최근 글", "오래된 글");
    }

    @Test
    @DisplayName("7일보다 오래된 글은 since 컷오프로 제외된다")
    void excludesPostsOlderThanSinceCutoff() {
        createPost("최근 글", OffsetDateTime.now().minusHours(1));
        CommunityPost stale = createPost("오래된 글", OffsetDateTime.now().minusDays(10));
        bumpLikes(stale.getId(), 100);

        List<String> titles = communityPostRepository
                .findWeeklyPopular(null, null, OffsetDateTime.now().minusDays(7), PageRequest.of(0, 10))
                .map(CommunityPost::getTitle)
                .getContent();

        assertThat(titles).containsExactly("최근 글");
    }

    private CommunityPost createPost(String title, OffsetDateTime createdAt) {
        CommunityPost post = communityPostRepository.save(new CommunityPost(
                ownerId, "글쓴이", title, "내용", PostCategory.GENERAL, null, createdAt, createdAt));
        communityPostMetricsRepository.save(new CommunityPostMetrics(post.getId(), createdAt));
        return post;
    }

    private void bumpLikes(Long postId, int times) {
        for (int i = 0; i < times; i++) {
            communityPostMetricsRepository.incrementLikeCount(postId);
        }
    }

    private void bumpComments(Long postId, int times) {
        for (int i = 0; i < times; i++) {
            communityPostMetricsRepository.incrementCommentCount(postId);
        }
    }
}
