package com.kraft.user.mail;

import com.kraft.shared.web.BaseUrl;
import com.kraft.user.domain.EmailMasker;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * 아웃박스 메일을 보낸다. 선점({@code claimBatch})·적재({@code load})·결과 기록({@code markSent}/
 * {@code markFailed})은 각각 짧은 트랜잭션이고, SMTP 발송은 어떤 트랜잭션에도 속하지 않는다 —
 * {@code afterCommit}에서 보내도 커넥션은 아직 잡혀 있기 때문이다.
 * <p>
 * 가입·재발송 직후 {@code @Async}로 한 번, 그리고 주기적으로 돈다. 겹쳐 돌아도
 * {@code FOR UPDATE SKIP LOCKED}로 같은 메일을 두 번 집지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxMailWorker {

    private final OutboxMailStore store;
    private final EmailSender emailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.mail.batch-size:20}")
    private int batchSize;

    /** {@code false}면 예약·즉시 발송을 모두 멈춘다(보관 기간 정리는 별개). */
    @Value("${app.mail.enabled:true}")
    private boolean enabled;

    /** 이 시간 넘게 SENDING이면 발송 도중 중단된 것으로 보고 되돌린다. */
    @Value("${app.mail.stuck-after-ms:300000}")
    private long stuckAfterMs;

    /** 종료 상태(SENT/FAILED) 행을 이 기간이 지나면 지운다. */
    @Value("${app.mail.retention-days:30}")
    private int retentionDays;

    /** {@link #cleanupOldTerminal} 전용 스위치. {@code enabled}로는 꺼지지 않는다. */
    @Value("${app.mail.retention-enabled:true}")
    private boolean retentionEnabled = true;

    /** 동시에 SMTP로 보내는 최대 개수(프로세스 전체). */
    @Value("${app.mail.max-concurrent-sends:5}")
    private int maxConcurrentSends;

    /** 모든 {@code drain()} 호출이 공유한다 — 겹쳐 돌아도 총 동시 발송 수가 상한을 넘지 않게. */
    private Semaphore sendPermits;

    /** 0 이하 설정은 기동 시 막는다 — 동시성 0이면 발송이 조용히 멈추고, 음수 batchSize는 SQL LIMIT 오류다. */
    @PostConstruct
    void initSendPermits() {
        if (maxConcurrentSends < 1) {
            throw new IllegalStateException(
                    "app.mail.max-concurrent-sends는 1 이상이어야 합니다: " + maxConcurrentSends);
        }
        if (batchSize < 1) {
            throw new IllegalStateException("app.mail.batch-size는 1 이상이어야 합니다: " + batchSize);
        }
        this.sendPermits = new Semaphore(maxConcurrentSends);
    }

    /** 가입·재발송 직후 곧바로 한 번 돌린다. 호출한 요청의 트랜잭션·스레드와 분리된다. */
    @Async
    public void drainAsync() {
        drain();
    }

    @Scheduled(initialDelayString = "${app.mail.drain-initial-delay-ms:30000}",
            fixedDelayString = "${app.mail.drain-interval-ms:60000}")
    public void drainScheduled() {
        if (!enabled) {
            return;
        }
        // 정체(SENDING인 채 남은 행) 재처리는 안전망이라 예약 경로에서만 한다.
        store.requeueStuck(LocalDateTime.now().minus(Duration.ofMillis(stuckAfterMs)));
        drain();
    }

    /**
     * 한 배치를 선점해 가상 스레드로 동시에 보내고, 배치가 끝날 때까지 기다린다. 두 진입점 모두 여기서
     * {@code enabled}를 검사한다.
     * <p>
     * 임대 토큰은 호출마다 새로 만든다 — 공유하면 재선점된 행에 늦게 도착한 이전 시도의 결과가 새 시도를
     * 덮어쓸 수 있다. 겹친 호출은 막지 않는다({@link #sendPermits}가 총 동시성을 제한한다).
     */
    public void drain() {
        if (!enabled) {
            return;
        }
        String ownerToken = UUID.randomUUID().toString();
        List<Long> ids = store.claimBatch(batchSize, ownerToken);
        if (ids.isEmpty()) {
            return;
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = ids.stream()
                    .<Future<?>>map(id -> executor.submit(() -> sendWithPermit(id, ownerToken, sendPermits)))
                    .toList();
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("메일 배치 발송 중 예외가 발생했습니다.", e);
        }
    }

    private void sendWithPermit(Long id, String ownerToken, Semaphore permits) {
        try {
            permits.acquire();
            try {
                store.load(id, ownerToken).ifPresent(this::send);
            } finally {
                permits.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 오래된 SENT/FAILED 행을 지운다. 발송을 꺼도 테이블이 자라지 않게 {@code enabled}와 무관하다. */
    @Scheduled(initialDelayString = "${app.mail.retention-initial-delay-ms:60000}",
            fixedDelayString = "${app.mail.retention-interval-ms:86400000}")
    public void cleanupOldTerminal() {
        if (!retentionEnabled) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minus(Duration.ofDays(retentionDays));
        long removed = store.deleteOldTerminal(threshold);
        if (removed > 0) {
            log.info("보관 기한이 지난 아웃박스 메일 {}건을 정리했습니다.", removed);
        }
    }

    private void send(OutboxMailStore.PendingMail mail) {
        try {
            emailSender.send(mail.to(), subject(mail.kind()), body(mail.kind(), mail.token()));
            store.markSent(mail.id(), mail.ownerToken());
        } catch (Exception e) {
            // 한 통이 실패해도 나머지는 계속 보내고, 원인을 행에 남겨 다시 시도한다.
            // SMTP 오류 메시지에 담긴 수신자 주소는 가린다.
            String maskedMessage = maskRecipient(e.getMessage(), mail.to());
            // e를 로거에 넘기면 가리지 않은 메시지가 찍히므로 타입과 가린 메시지만 남긴다.
            log.warn("메일 발송에 실패했습니다. outboxMailId={}, exceptionType={}, detail={}",
                    mail.id(), e.getClass().getSimpleName(), maskedMessage);
            store.markFailed(mail.id(), maskedMessage, mail.ownerToken());
        }
    }

    private static String maskRecipient(String message, String recipient) {
        if (message == null) {
            return null;
        }
        return message.replace(recipient, EmailMasker.mask(recipient));
    }

    private String subject(OutboxMailKind kind) {
        return switch (kind) {
            case VERIFY_EMAIL -> "[kraft] 이메일 인증을 완료해 주세요";
            case PASSWORD_RESET -> "[kraft] 비밀번호 재설정 링크입니다";
            case LOGIN_ATTEMPTS_WARNING -> "[kraft] 로그인 시도가 많았습니다";
            case ACCOUNT_EXISTS -> "[kraft] 이미 가입된 계정입니다";
        };
    }

    /** 본문은 링크와 유효 시간뿐이다 — 잘못 배달돼도 계정 정보가 드러나지 않게. */
    private String body(OutboxMailKind kind, String token) {
        return switch (kind) {
            // 토큰은 프래그먼트(#)에 싣는다 — 서버·프록시 접근 로그에 남지 않는다.
            case VERIFY_EMAIL -> "아래 링크를 클릭해 이메일 인증을 완료해 주세요:\n"
                    + BaseUrl.normalize(baseUrl) + "/users/verify#token=" + token
                    + "\n\n이 링크는 24시간 동안 유효합니다.";
            case PASSWORD_RESET -> "아래 링크에서 새 비밀번호를 정해 주세요:\n"
                    + BaseUrl.normalize(baseUrl) + "/users/password-reset#token=" + token
                    + "\n\n이 링크는 30분 동안 한 번만 사용할 수 있습니다."
                    + "\n요청한 적이 없다면 이 메일을 무시하세요. 비밀번호는 그대로입니다.";
            case LOGIN_ATTEMPTS_WARNING -> "회원님 계정에 짧은 시간 동안 로그인 시도가 많았습니다."
                    + "\n본인이 아니라면 비밀번호를 바꾸는 것을 권장합니다."
                    + "\n\n반복된 실패가 이어지면 계정은 잠시 잠기며, 시간이 지나면 저절로 풀립니다.";
            case ACCOUNT_EXISTS -> "이미 이 메일 주소로 가입된 계정이 있습니다."
                    + "\n본인이라면 로그인하거나, 비밀번호를 잊으셨다면 재설정을 이용해 주세요."
                    + "\n본인이 가입을 시도한 적이 없다면 이 메일을 무시하세요.";
        };
    }
}
