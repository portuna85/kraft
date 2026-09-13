package com.kraft.service.user;

import com.kraft.domain.user.EmailVerificationTokenRepository;
import com.kraft.domain.user.OutboxMail;
import com.kraft.domain.user.OutboxMailRepository;
import com.kraft.domain.user.OutboxMailStatus;
import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;

/**
 * 메일 발송이 DB 트랜잭션 밖에서 일어나는지, 실패해도 재시도되는지를 실제 DB로 검증한다
 * (개선 보고서 "메일 안정성").
 * <p>
 * 예전에는 회원가입 트랜잭션 안에서 SMTP를 그대로 호출했다. 연결·읽기·쓰기 타임아웃이 각각
 * 5초라 메일 서버가 굼뜨면 DB 커넥션 하나를 최대 15초 붙잡았다.
 * <p>
 * 대부분의 테스트는 {@code store.enqueue()}로 대기열에만 넣고 발송을 직접 부른다.
 * {@code sendVerificationEmail()}을 쓰면 커밋 직후 {@code @Async} 발송이 함께 돌아
 * "누가 먼저 집었는가"가 매번 달라지기 때문이다. 그 비동기 경로는 마지막 테스트가 따로 본다.
 */
@SpringBootTest
class OutboxMailTransactionTest {

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private OutboxMailWorker outboxMailWorker;

    @Autowired
    private OutboxMailStore outboxMailStore;

    @Autowired
    private OutboxMailRepository outboxMailRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    /** 진짜 SMTP 대신 대역을 쓴다. 발송 시점의 트랜잭션 상태를 들여다보기 위해서다. */
    @MockitoBean
    private EmailSender emailSender;

    private User user;

    @BeforeEach
    void setUp() {
        outboxMailRepository.deleteAll();
        tokenRepository.deleteAll();

        String unique = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder()
                .name("outbox-" + unique)
                .email("outbox-" + unique + "@example.com")
                .password("encoded")
                .role(Role.GUEST)
                .build());
    }

    /** 발송 흐름만 보고 싶을 때 쓴다. 비동기 발송이 함께 돌지 않는다. */
    private void queueOne() {
        outboxMailStore.enqueue(user, UUID.randomUUID().toString());
    }

    /**
     * 이 테스트가 이 작업의 핵심 주장을 고정한다.
     * <p>
     * "커밋 후에 보내면 되지 않나"로는 부족하다. Spring은 {@code afterCommit} 콜백을 커넥션을
     * 반납하는 {@code cleanupAfterCompletion}보다 먼저 실행하므로, 그 안에서 SMTP를 기다리면
     * 커넥션은 여전히 잡혀 있다. 그래서 발송은 아예 다른 실행 흐름이어야 한다.
     */
    @Test
    @DisplayName("발송 시점에는 어떤 트랜잭션에도 속해 있지 않다")
    void sendingHappensOutsideAnyTransaction() {
        AtomicBoolean insideTransaction = new AtomicBoolean(true);
        willAnswer(invocation -> {
            insideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).given(emailSender).send(anyString(), anyString(), anyString());

        queueOne();
        outboxMailWorker.drain();

        assertThat(insideTransaction)
                .as("SMTP를 부르는 동안 트랜잭션이 열려 있으면 DB 커넥션도 잡고 있는 것이다")
                .isFalse();
    }

    @Test
    @DisplayName("메일은 대기열에 들어가고, 보내고 나면 SENT로 남는다")
    void mailIsQueuedThenMarkedSent() {
        queueOne();

        List<OutboxMail> queued = outboxMailRepository.findAll();
        assertThat(queued).hasSize(1);
        assertThat(queued.get(0).getStatus()).isEqualTo(OutboxMailStatus.PENDING);

        outboxMailWorker.drain();

        OutboxMail sent = outboxMailRepository.findAll().get(0);
        assertThat(sent.getStatus()).isEqualTo(OutboxMailStatus.SENT);
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(sent.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("발송이 실패하면 원인을 남기고 다음 차례에 다시 시도한다")
    void failedSendIsRetried() {
        willThrow(new RuntimeException("메일 서버가 응답하지 않습니다"))
                .given(emailSender).send(anyString(), anyString(), anyString());

        queueOne();
        outboxMailWorker.drain();

        OutboxMail failed = outboxMailRepository.findAll().get(0);
        // 아직 기회가 남았으므로 PENDING으로 돌아가 다음 차례를 기다린다.
        assertThat(failed.getStatus()).isEqualTo(OutboxMailStatus.PENDING);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("응답하지 않습니다");

        outboxMailWorker.drain();
        assertThat(outboxMailRepository.findAll().get(0).getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("발송 도중 중단되어 SENDING으로 남은 메일은 다시 대기열로 돌아온다")
    void stuckMailIsRequeued() {
        queueOne();
        assertThat(outboxMailStore.claimBatch(10)).hasSize(1);
        assertThat(outboxMailRepository.findAll().get(0).getStatus()).isEqualTo(OutboxMailStatus.SENDING);

        // 프로세스가 여기서 죽었다면 이 행은 영영 아무도 집지 않는다.
        int requeued = outboxMailStore.requeueStuck(LocalDateTime.now().plusMinutes(1));

        assertThat(requeued).isEqualTo(1);
        assertThat(outboxMailRepository.findAll().get(0).getStatus()).isEqualTo(OutboxMailStatus.PENDING);
    }

    @Test
    @DisplayName("재발송을 연달아 요청하면 거부하고 얼마나 기다려야 하는지 알려준다")
    void resendIsRateLimited() {
        String email = user.getEmail();
        emailVerificationService.resend(email);

        assertThatThrownBy(() -> emailVerificationService.resend(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("초 후에 다시 시도해 주세요");

        // 거부된 요청은 대기열을 늘리지 않는다.
        assertThat(outboxMailRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("인증 메일을 요청하면 주기 작업을 기다리지 않고 곧 발송된다")
    void requestingVerificationMailSendsWithoutWaitingForSchedule() {
        emailVerificationService.sendVerificationEmail(user.getEmail());

        // 커밋 직후 @Async 발송이 다른 스레드에서 돈다. 주기 작업(기본 60초)을 기다리지 않는다.
        awaitUntil(() -> outboxMailRepository.findAll().stream()
                .allMatch(mail -> mail.getStatus() == OutboxMailStatus.SENT));

        assertThat(outboxMailRepository.findAll()).singleElement()
                .satisfies(mail -> assertThat(mail.getSentAt()).isNotNull());
    }

    /** 조건이 참이 될 때까지 짧게 기다린다. 비동기 경로라 즉시 참이 아닐 수 있다. */
    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("10초 안에 조건이 만족되지 않았습니다.");
    }
}
