package com.kraft.user.service;

import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailVerificationToken;
import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.PasswordResetToken;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰 소비·재발급의 동시성을 실제 스레드·실제 DB 행 잠금으로 검증한다(B07).
 * <p>
 * 두 요청을 같은 스레드에서 트랜잭션 템플릿으로 중첩 호출하는 방식({@code PostImageLifecycleTest}가
 * 낙관적 잠금 검증에 쓰는 방식)은 여기서 쓸 수 없다 — 토큰 소비의 조건부 DELETE와 재발급의
 * 비관적 잠금은 모두 <b>DB 행 잠금으로 블로킹</b>되므로, 바깥 트랜잭션이 아직 커밋하지 않은
 * 채 같은 스레드에서 안쪽 트랜잭션을 동기 호출하면 서로를 기다리며 그대로 멈춘다(자기 교착).
 * 그래서 실제로 동시에 도는 두 스레드를 띄워 검증한다.
 */
@SpringBootTest
class TokenConcurrencyTest {

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private EmailVerificationTokenRepository emailTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        emailTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder()
                .name("concurrency-" + unique)
                .email("concurrency-" + unique + "@example.com")
                .password("encoded")
                .role(Role.GUEST)
                .build());
    }

    @Test
    @DisplayName("B07: 같은 인증 토큰을 동시에 두 번 소비하면 정확히 한쪽만 성공하고, 승격은 한 번만 일어난다")
    void verify_concurrentConsumptionOfSameToken_onlyOneSucceeds() throws InterruptedException {
        String token = saveEmailToken(LocalDateTime.now().plusHours(1));

        List<Boolean> results = new CopyOnWriteArrayList<>();
        runTogether(
                () -> attemptVerify(token, results),
                () -> attemptVerify(token, results));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
        assertThat(emailTokenRepository.findByTokenHash(EmailHasher.sha512Hex(token))).isEmpty();
    }

    @Test
    @DisplayName("B07: 같은 비밀번호 재설정 토큰을 동시에 두 번 소비하면 정확히 한쪽만 성공한다")
    void reset_concurrentConsumptionOfSameToken_onlyOneSucceeds() throws InterruptedException {
        String token = savePasswordResetToken(LocalDateTime.now().plusMinutes(10));

        List<Boolean> results = new CopyOnWriteArrayList<>();
        runTogether(
                () -> attemptReset(token, "NewPass1!", results),
                () -> attemptReset(token, "OtherPass2!", results));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(passwordResetTokenRepository.findByTokenHash(EmailHasher.sha512Hex(token))).isEmpty();
    }

    private void attemptVerify(String token, List<Boolean> results) {
        try {
            emailVerificationService.verify(token);
            results.add(true);
        } catch (IllegalArgumentException e) {
            results.add(false);
        }
    }

    private void attemptReset(String token, String newPassword, List<Boolean> results) {
        try {
            passwordResetService.reset(token, newPassword);
            results.add(true);
        } catch (IllegalArgumentException e) {
            results.add(false);
        }
    }

    /** 두 작업을 준비된 순간 함께 시작해, 둘 다 거의 같은 시점에 DB에 도달하게 한다. */
    private void runTogether(Runnable first, Runnable second) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(2);
        Thread t1 = new Thread(() -> awaitAndRun(ready, first));
        Thread t2 = new Thread(() -> awaitAndRun(ready, second));
        t1.start();
        t2.start();
        t1.join(10_000);
        t2.join(10_000);
        // 타임아웃 안에 안 끝났으면(교착 등) 여기서 바로 드러낸다(OPS-B1).
        if (t1.isAlive() || t2.isAlive()) {
            throw new AssertionError("동시 실행 스레드가 타임아웃 안에 끝나지 않았습니다.");
        }
    }

    private void awaitAndRun(CountDownLatch ready, Runnable action) {
        ready.countDown();
        try {
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("두 스레드가 타임아웃 안에 준비되지 않았습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        action.run();
    }

    private String saveEmailToken(LocalDateTime expiresAt) {
        String token = UUID.randomUUID().toString();
        emailTokenRepository.save(EmailVerificationToken.builder()
                .tokenHash(EmailHasher.sha512Hex(token))
                .user(user)
                .expiresAt(expiresAt)
                .build());
        return token;
    }

    private String savePasswordResetToken(LocalDateTime expiresAt) {
        String token = UUID.randomUUID().toString();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .tokenHash(EmailHasher.sha512Hex(token))
                .user(user)
                .expiresAt(expiresAt)
                .build());
        return token;
    }
}
