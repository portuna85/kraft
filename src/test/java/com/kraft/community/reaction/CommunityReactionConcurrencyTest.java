package com.kraft.community.reaction;

import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.PostCategory;
import com.kraft.community.post.CommunityPostMetrics;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KB-02: like()/bookmark()가 exists 확인 후 save()해 DataIntegrityViolationException을
 * catch하던 방식은 Mockito mock으로만 검증돼 있었다 — CommunityPostLike는 IDENTITY라 save()
 * 즉시 INSERT가 나가고, 유니크 위반이 Hibernate 세션을 rollback-only로 마킹하면 catch로
 * 잡아도 커밋 시점에 UnexpectedRollbackException이 날 수 있는 경로였다. upsert 전환 후
 * 이 경합이 실제 MariaDB에서 예외 없이 흡수되는지, like_count가 정확히 1로 남는지 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("커뮤니티 리액션 동시성 테스트 (실 MariaDB)")
class CommunityReactionConcurrencyTest {

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
    private CommunityReactionService communityReactionService;

    @Autowired
    private CommunityPostRepository communityPostRepository;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Autowired
    private CommunityPostMetricsRepository communityPostMetricsRepository;

    @Autowired
    private CommunityPostLikeRepository communityPostLikeRepository;

    @Autowired
    private CommunityPostBookmarkRepository communityPostBookmarkRepository;

    private Long postId;
    private Long userId;

    @BeforeEach
    void setUp() {
        communityPostLikeRepository.deleteAll();
        communityPostBookmarkRepository.deleteAll();
        communityPostMetricsRepository.deleteAll();
        communityPostRepository.deleteAll();
        communityUserRepository.deleteAll();

        OffsetDateTime now = OffsetDateTime.now();
        CommunityUser owner = communityUserRepository.save(
                new CommunityUser("google", "owner-" + System.nanoTime(), "글쓴이", null, now));
        CommunityUser liker = communityUserRepository.save(
                new CommunityUser("naver", "liker-" + System.nanoTime(), "좋아요누른사람", null, now));
        userId = liker.getId();

        CommunityPost post = communityPostRepository.save(new CommunityPost(
                owner.getId(), "글쓴이", "제목", "내용", PostCategory.GENERAL, null, now, now));
        postId = post.getId();
        communityPostMetricsRepository.save(new CommunityPostMetrics(postId, now));
    }

    @Test
    @DisplayName("두 스레드가 동시에 같은 글에 좋아요를 눌러도 예외 없이 끝나고 like_count는 1이다")
    void concurrentLike_absorbsRaceWithoutException() throws Exception {
        int threadCount = 4;

        runConcurrently(threadCount, () -> {
            communityReactionService.like(postId, userId);
            return null;
        });

        assertThat(communityPostLikeRepository.findByPostIdAndUserId(postId, userId)).isPresent();
        assertThat(communityPostMetricsRepository.findByPostId(postId).orElseThrow().getLikeCount()).isEqualTo(1);
        // M-01: insert와 집계 증가가 별도 트랜잭션에 걸쳐 있으면 "행은 있는데 집계가 밀린"
        // 드리프트가 가능했다 — 실제 좋아요 행 수와 집계값이 항상 일치하는지 직접 비교한다.
        long actualLikeRows = communityPostLikeRepository.findByUserIdAndPostIdIn(userId, List.of(postId)).size();
        assertThat(communityPostMetricsRepository.findByPostId(postId).orElseThrow().getLikeCount())
                .isEqualTo(actualLikeRows);
    }

    @Test
    @DisplayName("두 스레드가 동시에 같은 글을 북마크해도 예외 없이 끝나고 행은 하나만 남는다")
    void concurrentBookmark_absorbsRaceWithoutException() throws Exception {
        int threadCount = 4;

        runConcurrently(threadCount, () -> {
            communityReactionService.bookmark(postId, userId);
            return null;
        });

        List<CommunityPostBookmark> bookmarks = communityPostBookmarkRepository.findByUserIdAndPostIdIn(userId, List.of(postId));
        assertThat(bookmarks).hasSize(1);
    }

    @Test
    @DisplayName("KB-06: 같은 사용자가 동시에 여러 번 unlike해도 like_count는 정확히 1만 줄어든다")
    void concurrentUnlike_decrementsExactlyOnce() throws Exception {
        // 다른 사용자 둘이 먼저 좋아요를 남겨 like_count를 3으로 만든다 — unlike 대상 행은
        // targetUser 것 하나뿐이라, 실제 삭제는 어느 스레드든 딱 1번만 성공해야 한다.
        // (버그가 있으면 매 스레드가 findBy...isPresent() 통과 후 무조건 감산해 3→0까지
        // 깎일 수 있다 — 올바른 결과는 3→2.)
        CommunityUser otherLiker1 = communityUserRepository.save(
                new CommunityUser("google", "other1-" + System.nanoTime(), "다른사람1", null, OffsetDateTime.now()));
        CommunityUser otherLiker2 = communityUserRepository.save(
                new CommunityUser("google", "other2-" + System.nanoTime(), "다른사람2", null, OffsetDateTime.now()));
        communityReactionService.like(postId, otherLiker1.getId());
        communityReactionService.like(postId, otherLiker2.getId());
        communityReactionService.like(postId, userId);
        assertThat(communityPostMetricsRepository.findByPostId(postId).orElseThrow().getLikeCount()).isEqualTo(3);

        int threadCount = 4;
        runConcurrently(threadCount, () -> {
            communityReactionService.unlike(postId, userId);
            return null;
        });

        assertThat(communityPostLikeRepository.findByPostIdAndUserId(postId, userId)).isEmpty();
        assertThat(communityPostMetricsRepository.findByPostId(postId).orElseThrow().getLikeCount()).isEqualTo(2);
    }

    private void runConcurrently(int threadCount, java.util.concurrent.Callable<Void> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Void>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return task.call();
                    }))
                    .toList();
            boolean allReady = ready.await(5, TimeUnit.SECONDS);
            assertThat(allReady).as("모든 스레드가 준비될 때까지 기다림").isTrue();
            start.countDown();

            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }
    }
}
