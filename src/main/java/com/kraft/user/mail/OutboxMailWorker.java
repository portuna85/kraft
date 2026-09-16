package com.kraft.user.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 아웃박스에 쌓인 메일을 실제로 보낸다.
 *
 * <h3>왜 이렇게까지 나누는가</h3>
 * "커밋 이후에 보내면 되지 않나"로는 부족하다. Spring은 {@code afterCommit} 콜백을
 * {@code cleanupAfterCompletion}<b>보다 먼저</b> 실행하므로, 그 안에서 SMTP를 기다리면
 * <b>DB 커넥션은 여전히 잡힌 채</b>다. 커넥션을 정말 놓으려면 발송이 그 트랜잭션 바깥,
 * 다른 실행 흐름에서 일어나야 한다.
 *
 * <h3>한 통을 보내는 절차</h3>
 * <ol>
 * <li>{@code claimBatch} — 짧은 트랜잭션에서 PENDING을 SENDING으로 바꾸고 id만 받는다</li>
 * <li>{@code load} — 짧은 트랜잭션에서 수신 주소·토큰을 평범한 값으로 꺼낸다</li>
 * <li><b>발송 — 어떤 트랜잭션에도 속하지 않는다</b></li>
 * <li>{@code markSent} / {@code markFailed} — 다시 짧은 트랜잭션</li>
 * </ol>
 * 3번 동안 DB 커넥션은 풀에 돌아가 있다. 메일 서버가 15초를 끌어도 다른 요청이 막히지 않는다.
 *
 * <h3>언제 도는가</h3>
 * 가입·재발송 직후 {@code @Async}로 한 번(사용자가 오래 기다리지 않게), 그리고 주기적으로 한 번
 * (앱이 내려갔거나 그때 실패한 것을 위해). 둘이 동시에 돌아도 {@code claimBatch}의 행 잠금
 * ({@code FOR UPDATE SKIP LOCKED}) 덕분에 같은 메일을 두 번 집지 않는다.
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

    @Value("${app.mail.enabled:true}")
    private boolean enabled;

    /** 이 시간 넘게 SENDING이면 발송 도중 중단된 것으로 보고 되돌린다. */
    @Value("${app.mail.stuck-after-ms:300000}")
    private long stuckAfterMs;

    /** 종료 상태(SENT/FAILED) 행을 이 기간이 지나면 지운다. */
    @Value("${app.mail.retention-days:30}")
    private int retentionDays;

    /** 가입·재발송 직후 곧바로 한 번 돌린다. 호출한 요청의 트랜잭션·스레드와 분리된다. */
    @Async
    public void drainAsync() {
        drain();
    }

    @Scheduled(initialDelayString = "${app.mail.drain-initial-delay-ms:30000}",
            fixedDelayString = "${app.mail.drain-interval-ms:60000}")
    public void drainScheduled() {
        drain();
    }

    /**
     * {@code enabled} 검사와 정체 재처리를 예약 실행에서만 하던 것을 여기로 옮겼다.
     * 예전에는 {@code app.mail.enabled=false}로 꺼도 가입 직후 {@code drainAsync}가 그대로
     * 발송을 진행했다 — 공유 진입점에서 한 번만 검사하면 두 경로 모두 같은 규칙을 따른다.
     */
    public void drain() {
        if (!enabled) {
            return;
        }
        store.requeueStuck(LocalDateTime.now().minus(Duration.ofMillis(stuckAfterMs)));
        List<Long> ids = store.claimBatch(batchSize);
        for (Long id : ids) {
            store.load(id).ifPresent(this::send);
        }
    }

    /**
     * 종료된 지 오래된 SENT/FAILED 행을 지운다. 발송 자체와는 다른 관심사이므로
     * {@code enabled} 플래그와 무관하게 항상 돈다 — 발송을 끄더라도 이력 정리는 계속되어야
     * 테이블이 무한정 자라지 않는다.
     */
    @Scheduled(initialDelayString = "${app.mail.retention-initial-delay-ms:60000}",
            fixedDelayString = "${app.mail.retention-interval-ms:86400000}")
    public void cleanupOldTerminal() {
        LocalDateTime threshold = LocalDateTime.now().minus(Duration.ofDays(retentionDays));
        long removed = store.deleteOldTerminal(threshold);
        if (removed > 0) {
            log.info("보관 기한이 지난 아웃박스 메일 {}건을 정리했습니다.", removed);
        }
    }

    private void send(OutboxMailStore.PendingMail mail) {
        try {
            emailSender.send(mail.to(), subject(mail.kind()), body(mail.kind(), mail.token()));
            store.markSent(mail.id());
        } catch (Exception e) {
            // 한 통이 실패해도 나머지는 계속 보낸다. 원인은 행에 남겨 다음 차례에 다시 시도한다.
            log.warn("메일 발송에 실패했습니다. outboxMailId={}", mail.id(), e);
            store.markFailed(mail.id(), e.getMessage());
        }
    }

    private String subject(OutboxMailKind kind) {
        return switch (kind) {
            case VERIFY_EMAIL -> "[kraft] 이메일 인증을 완료해 주세요";
            case PASSWORD_RESET -> "[kraft] 비밀번호 재설정 링크입니다";
        };
    }

    /**
     * 본문은 링크 하나와 유효 시간이 전부다. 누가 요청했는지·어떤 계정인지는 적지 않는다 —
     * 메일이 잘못 배달되어도 그 자체로는 알려주는 것이 없어야 한다.
     */
    private String body(OutboxMailKind kind, String token) {
        return switch (kind) {
            case VERIFY_EMAIL -> "아래 링크를 클릭해 이메일 인증을 완료해 주세요:\n"
                    + baseUrl + "/users/verify?token=" + token
                    + "\n\n이 링크는 24시간 동안 유효합니다.";
            case PASSWORD_RESET -> "아래 링크에서 새 비밀번호를 정해 주세요:\n"
                    + baseUrl + "/users/password-reset?token=" + token
                    + "\n\n이 링크는 30분 동안 한 번만 사용할 수 있습니다."
                    + "\n요청한 적이 없다면 이 메일을 무시하세요. 비밀번호는 그대로입니다.";
        };
    }
}
