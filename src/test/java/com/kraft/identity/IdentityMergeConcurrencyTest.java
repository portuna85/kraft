package com.kraft.identity;

import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import com.kraft.saved.CreateSavedNumberRequest;
import com.kraft.saved.SavedNumberRepository;
import com.kraft.saved.SavedNumbersService;
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
 * M-02: claim()이 계정(owner) 행을 saveForOwner와 같은 락으로 잠그게 된 뒤, 서로 다른
 * 기기의 동시 claim이나 claim과 직접 저장(saveForOwner)의 동시 실행이 계정 저장 한도를
 * 넘기지 않고, 예외 없이(교착 없이) 끝나는지 실 MariaDB로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("계정 귀속 동시성 테스트 (실 MariaDB)")
class IdentityMergeConcurrencyTest {

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
    private IdentityMergeService identityMergeService;

    @Autowired
    private SavedNumbersService savedNumbersService;

    @Autowired
    private SavedNumberRepository savedNumberRepository;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Autowired
    private DeviceClaimRepository deviceClaimRepository;

    private Long ownerId;

    @BeforeEach
    void setUp() {
        savedNumberRepository.deleteAll();
        deviceClaimRepository.deleteAll();
        communityUserRepository.deleteAll();

        CommunityUser owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "테스트유저", null, OffsetDateTime.now()));
        ownerId = owner.getId();
    }

    @Test
    @DisplayName("서로 다른 두 기기가 동시에 같은 계정으로 claim해도 한도(2)를 넘기지 않고 예외 없이 끝난다")
    void concurrentClaimFromTwoDevices_neverExceedsLimit() throws Exception {
        String tokenA = "device-a-" + System.nanoTime();
        String tokenB = "device-b-" + System.nanoTime();
        // 각 기기에 서로 다른 번호 2개씩 익명으로 저장 — 합치면 한도(2)를 넘는 4개다.
        savedNumbersService.save(tokenA, new CreateSavedNumberRequest(List.of(1, 2, 3, 4, 5, 6), null, "MANUAL"));
        savedNumbersService.save(tokenA, new CreateSavedNumberRequest(List.of(2, 3, 4, 5, 6, 7), null, "MANUAL"));
        savedNumbersService.save(tokenB, new CreateSavedNumberRequest(List.of(3, 4, 5, 6, 7, 8), null, "MANUAL"));
        savedNumbersService.save(tokenB, new CreateSavedNumberRequest(List.of(4, 5, 6, 7, 8, 9), null, "MANUAL"));

        runConcurrently(2, List.of(
                () -> identityMergeService.claim(tokenA, ownerId),
                () -> identityMergeService.claim(tokenB, ownerId)
        ));

        assertThat(savedNumberRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerId).size())
                .isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("claim과 saveForOwner가 동시에 실행돼도 계정 한도(2)를 넘기지 않고 교착 없이 끝난다")
    void concurrentClaimAndSaveForOwner_neverExceedsLimit() throws Exception {
        String token = "device-c-" + System.nanoTime();
        savedNumbersService.save(token, new CreateSavedNumberRequest(List.of(1, 2, 3, 4, 5, 6), null, "MANUAL"));
        savedNumbersService.save(token, new CreateSavedNumberRequest(List.of(2, 3, 4, 5, 6, 7), null, "MANUAL"));

        runConcurrently(2, List.of(
                () -> identityMergeService.claim(token, ownerId),
                () -> savedNumbersService.saveForOwner(ownerId,
                        new CreateSavedNumberRequest(List.of(9, 10, 11, 12, 13, 14), null, "MANUAL"))
        ));

        assertThat(savedNumberRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerId).size())
                .isLessThanOrEqualTo(2);
    }

    private void runConcurrently(int threadCount, List<java.util.concurrent.Callable<?>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            return tasks.get(i).call();
                        } catch (Exception e) {
                            return e;
                        }
                    }))
                    .toList();
            boolean allReady = ready.await(5, TimeUnit.SECONDS);
            assertThat(allReady).as("모든 스레드가 준비될 때까지 기다림").isTrue();
            start.countDown();

            for (Future<Object> future : futures) {
                // claim/saveForOwner 둘 다 예외를 던지지 않고 한도만 조용히 지켜야 한다 —
                // future.get()이 ExecutionException을 던지면(교착·예상 밖 예외) 테스트가 실패한다.
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }
    }
}
