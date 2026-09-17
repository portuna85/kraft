package com.kraft.user.mail;

import com.kraft.user.domain.EmailVerificationToken;
import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.service.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 겹친 트랜잭션을 만들기 위한 별도 템플릿. 기본 전파(REQUIRED)로는 바깥 트랜잭션에
     * 참여해 같은 커넥션·잠금을 공유하므로 "다른 트랜잭션이 아직 커밋 전이라 행이 잠겨 있다"를
     * 재현할 수 없다.
     */
    private TransactionTemplate requiresNew;

    /** 진짜 SMTP 대신 대역을 쓴다. 발송 시점의 트랜잭션 상태를 들여다보기 위해서다. */
    @MockitoBean
    private EmailSender emailSender;

    private User user;

    /** application.yml의 app.mail.max-attempts와 같은 값. 여기서 포기한다. */
    private static final int MAX_ATTEMPTS = 5;

    @BeforeEach
    void setUp() {
        requiresNew = new TransactionTemplate(transactionTemplate.getTransactionManager());
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

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

    /**
     * 발송 흐름만 보고 싶을 때 쓴다. 비동기 발송이 함께 돌지 않는다.
     * <p>
     * 실제 운영에서는 {@code EmailVerificationService}가 토큰 저장과 아웃박스 등록을 같은
     * 트랜잭션에서 함께 한다. {@code load()}가 발송 직전 토큰이 유효한지 확인하므로(F08),
     * 이 헬퍼도 짝이 되는 토큰 행을 함께 만들어야 "토큰이 유효하지 않다"는 이유로 발송이
     * 조용히 건너뛰어지는 것을 막을 수 있다.
     */
    private void queueOne() {
        String token = UUID.randomUUID().toString();
        tokenRepository.save(EmailVerificationToken.builder()
                .token(token).user(user).expiresAt(LocalDateTime.now().plusHours(24)).build());
        outboxMailStore.enqueue(user, token, OutboxMailKind.VERIFY_EMAIL);
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
    @DisplayName("F08: 발송이 실패하면 원인을 남기고, 다음 재시도 시각(backoff) 전에는 다시 집지 않는다")
    void failedSendIsRetriedOnlyAfterBackoff() {
        willThrow(new RuntimeException("메일 서버가 응답하지 않습니다"))
                .given(emailSender).send(anyString(), anyString(), anyString());

        queueOne();
        outboxMailWorker.drain();

        OutboxMail failed = outboxMailRepository.findAll().get(0);
        // 아직 기회가 남았으므로 PENDING으로 돌아가지만, 다음 시도 시각이 미래로 미뤄진다.
        assertThat(failed.getStatus()).isEqualTo(OutboxMailStatus.PENDING);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("응답하지 않습니다");
        assertThat(failed.getNextAttemptAt()).isAfter(LocalDateTime.now());

        // 재시도 시각이 되기 전이므로 곧바로 다시 돌려도 집히지 않는다.
        outboxMailWorker.drain();
        assertThat(outboxMailRepository.findAll().get(0).getAttempts()).isEqualTo(1);

        // 재시도 시각이 지난 것처럼 시각을 당겨두면 다음 차례에 다시 집는다.
        backdateNextAttempt(failed.getId(), LocalDateTime.now().minusSeconds(1));
        outboxMailWorker.drain();
        assertThat(outboxMailRepository.findAll().get(0).getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("재시도 기회를 다 쓰면 FAILED로 끝나고 더는 집히지 않는다")
    void exhaustedRetriesEndAsFailed() {
        // 계속 실패하는 메일이 영원히 대기열을 돌면, 주기 작업이 매번 그 행부터 집어 뒤에 쌓인
        // 정상 메일을 늦춘다. 그래서 정해진 횟수에서 포기하고 사람이 볼 상태로 남긴다.
        willThrow(new RuntimeException("주소가 존재하지 않습니다"))
                .given(emailSender).send(anyString(), anyString(), anyString());

        queueOne();
        Long id = outboxMailRepository.findAll().get(0).getId();
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            // backoff 때문에 곧바로 재시도되지 않으므로, 매 차례 재시도 시각이 지난 것처럼 당겨둔다.
            backdateNextAttempt(id, LocalDateTime.now().minusSeconds(1));
            outboxMailWorker.drain();
        }

        OutboxMail givenUp = outboxMailRepository.findAll().get(0);
        assertThat(givenUp.getStatus()).isEqualTo(OutboxMailStatus.FAILED);
        assertThat(givenUp.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(givenUp.getLastError()).contains("존재하지 않습니다");

        // 더 돌려도 PENDING이 아니므로 집히지 않는다 — 시도 횟수가 그대로다.
        assertThat(outboxMailStore.claimBatch(10, "test-owner")).isEmpty();
        outboxMailWorker.drain();
        assertThat(outboxMailRepository.findAll().get(0).getAttempts()).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("F08: 발송 직전 인증 토큰이 재발급으로 사라졌으면 보내지 않고 즉시 FAILED로 남긴다")
    void skipsSendWhenTokenNoLongerValid() {
        queueOne();
        tokenRepository.deleteAll();

        outboxMailWorker.drain();

        OutboxMail mail = outboxMailRepository.findAll().get(0);
        assertThat(mail.getStatus()).isEqualTo(OutboxMailStatus.FAILED);
        assertThat(mail.getLastError()).contains("유효하지 않습니다");
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.never())
                .send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("F08: 한 배치의 동시 발송 수는 max-concurrent-sends를 넘지 않는다")
    void drain_limitsConcurrentSendsToConfiguredMaximum() throws InterruptedException {
        int total = 12;
        int maxConcurrent = (int) ReflectionTestUtils.getField(outboxMailWorker, "maxConcurrentSends");
        java.util.concurrent.atomic.AtomicInteger current = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger observedMax = new java.util.concurrent.atomic.AtomicInteger(0);
        willAnswer(invocation -> {
            int now = current.incrementAndGet();
            observedMax.updateAndGet(prev -> Math.max(prev, now));
            Thread.sleep(20);
            current.decrementAndGet();
            return null;
        }).given(emailSender).send(anyString(), anyString(), anyString());

        for (int i = 0; i < total; i++) {
            queueOne();
        }
        ReflectionTestUtils.setField(outboxMailWorker, "batchSize", total);
        outboxMailWorker.drain();

        assertThat(observedMax.get()).isLessThanOrEqualTo(maxConcurrent);
        assertThat(outboxMailRepository.findAll()).allMatch(mail -> mail.getStatus() == OutboxMailStatus.SENT);
    }

    /** next_attempt_at은 markFailed()가 지금 시각 기준으로 계산하므로, 지난 것처럼 만들려면 직접 당긴다. */
    private void backdateNextAttempt(Long id, LocalDateTime nextAttemptAt) {
        jdbcTemplate.update("UPDATE outbox_mails SET next_attempt_at = ? WHERE id = ?", nextAttemptAt, id);
    }

    @Test
    @DisplayName("오류 메시지가 아주 길어도 저장 한도(500자) 안으로 잘라 남긴다")
    void longErrorMessageIsTruncated() {
        willThrow(new RuntimeException("긴오류".repeat(300)))
                .given(emailSender).send(anyString(), anyString(), anyString());

        queueOne();
        outboxMailWorker.drain();

        // 컬럼은 500자다. 자르지 않으면 실패를 기록하려다 그 기록이 또 실패한다.
        assertThat(outboxMailRepository.findAll().get(0).getLastError()).hasSize(500);
    }

    @Test
    @DisplayName("발송 도중 중단되어 SENDING으로 남은 메일은 다시 대기열로 돌아온다")
    void stuckMailIsRequeued() {
        queueOne();
        assertThat(outboxMailStore.claimBatch(10, "test-owner")).hasSize(1);
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

    @Test
    @DisplayName("F01: 커밋 전인 선점은 다른 선점 시도가 같은 행을 집지 못하게 막는다")
    void claimBatch_holdsRowLockUntilCommit_soConcurrentClaimSkipsLockedRows() {
        for (int i = 0; i < 3; i++) {
            queueOne();
        }

        List<Long> outerIds = requiresNew.execute(status -> {
            List<Long> claimed = outboxMailStore.claimBatch(3, "outer-owner");

            // 바깥 트랜잭션이 아직 커밋 전이라 방금 집은 행은 잠긴 채다. 이 시점에 독립된
            // 트랜잭션이 같은 대상을 다시 선점하려 하면 SKIP LOCKED가 그 행들을 건너뛰어
            // 빈 목록을 돌려줘야 한다 — 예전 SELECT-then-UPDATE 방식이라면 잠금이 없어
            // 같은 행을 다시 집었을 것이다.
            List<Long> innerIds = requiresNew.execute(inner -> outboxMailStore.claimBatch(3, "inner-owner"));

            assertThat(innerIds).as("잠긴 행은 건너뛰어야 한다").isEmpty();
            return claimed;
        });

        assertThat(outerIds).hasSize(3);
        assertThat(outboxMailRepository.findAll())
                .allMatch(mail -> mail.getStatus() == OutboxMailStatus.SENDING);
    }

    @Test
    @DisplayName("F08: enabled=false면 drain이 아무 것도 집지 않는다")
    void whenDisabled_drainDoesNothing() {
        queueOne();
        ReflectionTestUtils.setField(outboxMailWorker, "enabled", false);
        try {
            outboxMailWorker.drain();
        } finally {
            ReflectionTestUtils.setField(outboxMailWorker, "enabled", true);
        }

        assertThat(outboxMailRepository.findAll().get(0).getStatus()).isEqualTo(OutboxMailStatus.PENDING);
    }

    @Test
    @DisplayName("F08: 보관 기한이 지난 SENT/FAILED만 정리하고 PENDING·최근 건은 남긴다")
    void deleteOldTerminal_removesOnlyStaleTerminalMails() {
        queueOne(); // PENDING, 최근
        Long staleSentId = enqueueAndMarkSent();
        Long freshSentId = enqueueAndMarkSent();

        backdateUpdatedAt(staleSentId, LocalDateTime.now().minusDays(40));

        long removed = outboxMailStore.deleteOldTerminal(LocalDateTime.now().minusDays(30));

        assertThat(removed).isEqualTo(1);
        Set<Long> remainingIds = new HashSet<>();
        outboxMailRepository.findAll().forEach(mail -> remainingIds.add(mail.getId()));
        assertThat(remainingIds).doesNotContain(staleSentId);
        assertThat(remainingIds).contains(freshSentId);
        assertThat(outboxMailRepository.count()).isEqualTo(2);
    }

    private Long enqueueAndMarkSent() {
        outboxMailStore.enqueue(user, UUID.randomUUID().toString(), OutboxMailKind.VERIFY_EMAIL);
        Long id = outboxMailRepository.findAll().stream()
                .filter(mail -> mail.getStatus() == OutboxMailStatus.PENDING)
                .findFirst().orElseThrow().getId();
        outboxMailStore.markSent(id);
        return id;
    }

    /**
     * updatedAt은 {@code @LastModifiedDate} 감사 필드라 엔티티를 통해서는 "지금"만 넣을 수
     * 있다. 오래전에 종료된 것처럼 만들려고 JDBC로 직접 갱신한다.
     */
    private void backdateUpdatedAt(Long id, LocalDateTime updatedAt) {
        jdbcTemplate.update("UPDATE outbox_mails SET updated_at = ? WHERE id = ?", updatedAt, id);
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
