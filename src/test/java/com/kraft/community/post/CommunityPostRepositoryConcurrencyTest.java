package com.kraft.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.common.error.ApiException;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
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

/**
 * 게시글 낙관적 잠금 버전 사전 검증 이후 flush 시점에 발생하는 동시 경합(update/delete 조합)이
 * 광역 500이 아니라 항상 리소스에 특정된 409로 변환되는지 실제 MariaDB로 검증한다(§P1-04).
 * CommunityUserRepositoryConcurrencyTest와 동일한 동시성 테스트 골격을 사용한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("게시글 수정·삭제 동시 경합 테스트 (실 MariaDB)")
class CommunityPostRepositoryConcurrencyTest {

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
    private CommunityPostService communityPostService;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    private Long ownerId;
    private Long postId;

    @BeforeEach
    void setUp() {
        CommunityUser owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "글쓴이", null, OffsetDateTime.now()));
        ownerId = owner.getId();
        CommunityPost post = communityPostService.create(
                ownerId, "글쓴이", null, new CreatePostRequest("제목", "내용", "GENERAL", null));
        postId = post.getId();
    }

    @Test
    @DisplayName("같은 버전을 대상으로 동시에 수정하면 하나만 성공하고 나머지는 409를 받는다")
    void concurrentUpdateUpdate_onlyOneSucceeds() throws Exception {
        List<Object> outcomes = runConcurrentlyAllowingFailure(2, i ->
                communityPostService.update(ownerId, postId, new UpdatePostRequest("새 제목 " + i, "새 내용 " + i, 0L)));

        assertExactlyOneSucceedsWithConflict(outcomes);
    }

    @Test
    @DisplayName("같은 버전을 대상으로 동시에 수정·삭제하면 하나만 성공하고 나머지는 409를 받는다")
    void concurrentUpdateDelete_onlyOneSucceeds() throws Exception {
        List<Object> outcomes = runConcurrentlyAllowingFailure(2, i -> {
            if (i == 0) {
                return communityPostService.update(ownerId, postId, new UpdatePostRequest("새 제목", "새 내용", 0L));
            }
            communityPostService.delete(ownerId, postId, 0L);
            return "deleted";
        });

        assertExactlyOneSucceedsWithConflict(outcomes);
    }

    @Test
    @DisplayName("같은 버전을 대상으로 동시에 삭제하면 하나만 성공하고 나머지는 409를 받는다")
    void concurrentDeleteDelete_onlyOneSucceeds() throws Exception {
        List<Object> outcomes = runConcurrentlyAllowingFailure(2, i -> {
            communityPostService.delete(ownerId, postId, 0L);
            return "deleted";
        });

        assertExactlyOneSucceedsWithConflict(outcomes);
    }

    @Test
    @DisplayName("LIKE 메타문자는 최신순·주간 인기 검색 모두에서 실제 MariaDB 리터럴로 취급된다")
    void searchLikeMetacharacters_areLiteralForEverySort() {
        communityPostService.create(ownerId, "글쓴이", null,
                new CreatePostRequest("정확 50%_sale! 행사", "검색 대상", "GENERAL", null));
        communityPostService.create(ownerId, "글쓴이", null,
                new CreatePostRequest("정확 50AAAsale! 행사", "와일드카드라면 잘못 포함될 글", "GENERAL", null));

        for (String sort : List.of("latest", "weekly_popular")) {
            List<String> titles = communityPostService.list(null, sort, "50%_sale!", 0, 20)
                    .map(CommunityPost::getTitle)
                    .getContent();

            assertThat(titles).containsExactly("정확 50%_sale! 행사");
        }
    }

    private void assertExactlyOneSucceedsWithConflict(List<Object> outcomes) {
        long succeeded = outcomes.stream().filter(o -> !(o instanceof ApiException)).count();
        long conflicted = outcomes.stream()
                .filter(o -> o instanceof ApiException apiException
                        && apiException.getStatus().value() == 409
                        && "COMMUNITY_POST_VERSION_CONFLICT".equals(apiException.getCode()))
                .count();

        assertThat(succeeded).isEqualTo(1);
        assertThat(conflicted).isEqualTo(1);
    }

    private List<Object> runConcurrentlyAllowingFailure(int threadCount, IntFunction<Object> task)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            return task.apply(i);
                        } catch (Exception e) {
                            return e;
                        }
                    }))
                    .toList();
            boolean allReady = ready.await(5, TimeUnit.SECONDS);
            assertThat(allReady).as("모든 스레드가 준비될 때까지 기다림").isTrue();
            start.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }
}
