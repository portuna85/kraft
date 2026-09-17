package com.kraft.user.service;

import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 계정의 재발송 쿨다운 검사·재발급 직렬화(B07, {@code UserRepository.findByIdForUpdate})를
 * 실제 행 잠금으로 검증한다.
 * <p>
 * 이 잠금은 <b>블로킹</b>이다 — 일부러 그렇다. 두 번째 요청은 즉시 실패하는 대신 첫 번째가
 * 끝날 때까지 짧게 기다린 뒤에야 쿨다운 검사를 하므로, "방금 보냈습니다"라는 정상 안내를
 * 받을 수 있다(즉시 실패하는 잠금이었다면 500 오류가 됐을 것이다). H2는 MVCC 특성상
 * {@code SELECT ... FOR UPDATE}가 이 블로킹을 보장하지 않아, MariaDB로만 검증한다
 * (개선 보고서 "H2 테스트의 잠금 오류 유형이 MariaDB의 타임아웃·교착 결과와 같다고 가정하지
 * 않는다"). Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ResendCooldownConcurrencyTest {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.7.2");

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxMailRepository outboxMailRepository;

    private User user;

    @BeforeEach
    void setUp() {
        outboxMailRepository.deleteAll();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder()
                .name("cooldown-" + unique)
                .email("cooldown-" + unique + "@example.com")
                .password("encoded")
                .role(Role.GUEST)
                .build());
    }

    @Test
    @DisplayName("B07: 같은 계정의 인증 메일 재발송 요청이 동시에 오면 쿨다운 검사가 직렬화되어 한쪽만 성공한다")
    void resend_concurrentRequestsForSameAccount_onlyOneSucceeds() throws InterruptedException {
        List<Boolean> results = new CopyOnWriteArrayList<>();
        String email = user.getEmail();

        CountDownLatch ready = new CountDownLatch(2);
        Runnable attempt = () -> {
            ready.countDown();
            try {
                ready.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                emailVerificationService.resend(email);
                results.add(true);
            } catch (IllegalArgumentException e) {
                results.add(false);
            }
        };
        Thread t1 = new Thread(attempt);
        Thread t2 = new Thread(attempt);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(results).containsExactlyInAnyOrder(true, false);
        // 재발급 성공은 한 번뿐이므로, 대기열에 쌓인 인증 메일도 하나여야 한다.
        assertThat(outboxMailRepository.count()).isEqualTo(1);
    }
}
