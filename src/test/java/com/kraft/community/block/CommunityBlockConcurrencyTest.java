package com.kraft.community.block;

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
 * M-03: block()의 find-then-save를 upsertBlock(ON DUPLICATE KEY UPDATE)으로 바꾼 뒤,
 * 동일한 (blocker, blocked) 쌍에 대한 동시 PUT이 실제로 예외 없이 흡수되고 행이
 * 정확히 하나만 남는지 실 MariaDB로 검증한다 — Mockito 목킹으로는 진짜 경합을
 * 재현할 수 없다(CommunityBlockServiceTest 주석 참고).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("커뮤니티 차단 동시성 테스트 (실 MariaDB)")
class CommunityBlockConcurrencyTest {

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
    private CommunityBlockService communityBlockService;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Autowired
    private CommunityUserBlockRepository communityUserBlockRepository;

    private Long blockerId;
    private Long blockedId;

    @BeforeEach
    void setUp() {
        communityUserBlockRepository.deleteAll();
        communityUserRepository.deleteAll();

        OffsetDateTime now = OffsetDateTime.now();
        CommunityUser blocker = communityUserRepository.save(
                new CommunityUser("google", "blocker-" + System.nanoTime(), "차단하는사람", null, now));
        CommunityUser blocked = communityUserRepository.save(
                new CommunityUser("naver", "blocked-" + System.nanoTime(), "차단당하는사람", null, now));
        blockerId = blocker.getId();
        blockedId = blocked.getId();
    }

    @Test
    @DisplayName("동일한 두 사용자를 여러 스레드가 동시에 차단해도 예외 없이 끝나고 행은 하나만 남는다")
    void concurrentBlock_absorbsRaceWithoutException() throws Exception {
        int threadCount = 4;

        runConcurrently(threadCount, () -> {
            communityBlockService.block(blockerId, blockedId);
            return null;
        });

        List<CommunityUserBlock> blocks = communityUserBlockRepository.findByBlockerUserId(blockerId);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).getBlockedUserId()).isEqualTo(blockedId);
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
