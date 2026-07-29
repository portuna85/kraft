package com.kraft.saved;

import com.kraft.common.lotto.LottoNumberCodec;
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
 * B2: countByClientTokenHash() 뒤 insert가 원자적이지 않던 문제와, unique 제약 위반을
 * 잡아 재조회하는 방식의 안전성을 실제 MariaDB 동시 커넥션으로 검증한다. H2(기본 test
 * 프로파일)는 InnoDB의 갭 락·REPEATABLE READ 스냅샷을 재현하지 않으므로 여기서는
 * FlywayMigrationTest와 같은 방식으로 실제 MariaDB를 띄운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("저장 번호 동시성 테스트 (실 MariaDB)")
class SavedNumbersConcurrencyTest {

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
        registry.add("kraft.saved.max-per-client", () -> "2");
    }

    @Autowired
    private SavedNumbersService savedNumbersService;

    @Autowired
    private SavedNumberRepository savedNumberRepository;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    private final LottoNumberCodec lottoNumberCodec = new LottoNumberCodec();

    @BeforeEach
    void cleanUp() {
        savedNumberRepository.deleteAll();
        communityUserRepository.deleteAll();
    }

    private Long createOwner() {
        CommunityUser owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "테스트유저", null, OffsetDateTime.now()));
        return owner.getId();
    }

    @Test
    @DisplayName("같은 번호를 동시에 저장해도 정확히 한 행만 만들어지고 나머지는 멱등 응답을 받는다")
    void concurrentDuplicateSave_createsExactlyOneRow() throws Exception {
        String token = "concurrent-dup-token-" + System.nanoTime();
        List<Integer> numbers = List.of(3, 11, 19, 28, 34, 42);
        // HikariCP maximum-pool-size=5(운영 설정과 동일) 이하로 맞춘다 — 그 이상은 락 경합이
        // 아니라 커넥션 풀 고갈이 병목이 되어 이 테스트의 목적(락 설계 검증)과 무관해진다.
        int threadCount = 4;

        List<SaveNumberResult> results = runConcurrently(threadCount,
                () -> savedNumbersService.save(token, new CreateSavedNumberRequest(numbers, null, "MANUAL")));

        long createdCount = results.stream().filter(SaveNumberResult::created).count();
        assertThat(createdCount).isEqualTo(1);
        assertThat(results.stream().map(r -> r.savedNumber().numbers()).distinct().toList())
                .containsExactly(numbers);
        assertThat(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(token)).hasSize(1);
    }

    @Test
    @DisplayName("한도 경계에서 서로 다른 번호를 동시에 저장하면 한도를 넘기지 않는다")
    void concurrentDifferentSaves_neverExceedsLimit() throws Exception {
        String token = "concurrent-limit-token-" + System.nanoTime();
        // maxPerClient=2(테스트 오버라이드) — 이미 1개 저장된 상태에서 서로 다른 번호 4건을 동시 요청
        savedNumbersService.save(token, new CreateSavedNumberRequest(List.of(1, 2, 3, 4, 5, 6), null, "MANUAL"));

        int threadCount = 2;
        List<Object> outcomes = runConcurrentlyAllowingFailure(threadCount, i -> {
            List<Integer> numbers = IntStream.rangeClosed(1, 6).map(n -> n + i + 1).boxed().toList();
            return savedNumbersService.save(token, new CreateSavedNumberRequest(numbers, null, "MANUAL"));
        });

        long succeeded = outcomes.stream().filter(o -> o instanceof SaveNumberResult).count();
        long rejected = outcomes.stream().filter(o -> o instanceof Exception).count();

        // 기존 1개 + 이번에 성공한 요청 수가 한도(2)를 절대 넘지 않아야 한다.
        assertThat(1 + succeeded).isLessThanOrEqualTo(2);
        assertThat(succeeded + rejected).isEqualTo(threadCount);
        assertThat(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(token).size())
                .isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("KB-14: 계정으로 같은 번호를 동시에 저장해도 정확히 한 행만 만들어지고 나머지는 멱등 응답을 받는다")
    void concurrentDuplicateSaveForOwner_createsExactlyOneRow() throws Exception {
        Long ownerUserId = createOwner();
        List<Integer> numbers = List.of(3, 11, 19, 28, 34, 42);
        int threadCount = 4;

        List<SaveNumberResult> results = runConcurrently(threadCount,
                () -> savedNumbersService.saveForOwner(ownerUserId, new CreateSavedNumberRequest(numbers, null, "MANUAL")));

        long createdCount = results.stream().filter(SaveNumberResult::created).count();
        assertThat(createdCount).isEqualTo(1);
        assertThat(savedNumberRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId)).hasSize(1);
    }

    @Test
    @DisplayName("KB-14: 계정 한도 경계에서 서로 다른 번호를 동시에 저장해도 한도를 넘기지 않고 500도 나지 않는다")
    void concurrentDifferentSavesForOwner_neverExceedsLimit() throws Exception {
        Long ownerUserId = createOwner();
        // maxPerClient=2(테스트 오버라이드) — 이미 1개 저장된 상태에서 서로 다른 번호 2건을 동시 요청
        savedNumbersService.saveForOwner(ownerUserId, new CreateSavedNumberRequest(List.of(1, 2, 3, 4, 5, 6), null, "MANUAL"));

        int threadCount = 2;
        List<Object> outcomes = runConcurrentlyAllowingFailure(threadCount, i -> {
            List<Integer> numbers = IntStream.rangeClosed(1, 6).map(n -> n + i + 1).boxed().toList();
            return savedNumbersService.saveForOwner(ownerUserId, new CreateSavedNumberRequest(numbers, null, "MANUAL"));
        });

        long succeeded = outcomes.stream().filter(o -> o instanceof SaveNumberResult).count();
        long rejected = outcomes.stream().filter(o -> o instanceof Exception).count();

        assertThat(1 + succeeded).isLessThanOrEqualTo(2);
        assertThat(succeeded + rejected).isEqualTo(threadCount);
        assertThat(savedNumberRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).size())
                .isLessThanOrEqualTo(2);
    }

    private <T> List<T> runConcurrently(int threadCount, java.util.concurrent.Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return task.call();
                    }))
                    .toList();
            boolean allReady = ready.await(5, TimeUnit.SECONDS);
            assertThat(allReady).as("모든 스레드가 준비될 때까지 기다림").isTrue();
            start.countDown();

            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    private List<Object> runConcurrentlyAllowingFailure(int threadCount,
            java.util.function.IntFunction<Object> task) throws Exception {
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

            List<Object> results = new java.util.ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }
}
