package com.kraft.user.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 세션 폐기 태스크를 처리한다. 비밀번호 변경·재설정·탈퇴 커밋 직후 {@link #attemptNow}로 한 번 시도하고(빠른
 * 경로), 실패하거나 프로세스가 죽은 것은 DB에 남은 태스크를 {@link #drainScheduled}가 주기적으로 집어 끝내 완수한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SessionRevocationWorker {

    private final SessionRevocationStore store;

    /** {@code false}면 예약 실행과 {@link #attemptNow}를 모두 막는다 — 이메일 키 교체(rekey) 창에서 {@code application-rekey.yml}이 끈다. */
    @Value("${app.session-revocation.enabled:true}")
    private boolean enabled;

    @Value("${app.session-revocation.batch-size:20}")
    private int batchSize;

    /** 이 시간 넘게 PROCESSING이면 처리 도중 중단된 것으로 보고 되돌린다. */
    @Value("${app.session-revocation.stuck-after-ms:300000}")
    private long stuckAfterMs;

    /** 종료 상태(DONE/FAILED) 행을 이 기간이 지나면 지운다. */
    @Value("${app.session-revocation.retention-days:30}")
    private int retentionDays;

    /** {@link #cleanupOldTerminal} 전용 스위치. {@code enabled}로는 꺼지지 않는다. */
    @Value("${app.session-revocation.retention-enabled:true}")
    private boolean retentionEnabled = true;

    /**
     * 커밋 직후 방금 만든 태스크 하나를 곧바로 시도한다. 실패해도 {@link #drainScheduled}가 이어받는다.
     * <p>
     * 동기로 유지한다 — 응답이 세션이 실제로 끊긴 뒤에 돌아온다는 것이 핵심 보장이다. 소유 토큰은 호출마다 새로
     * 만든다(공유하면 재큐잉 뒤 늦게 도착한 이전 시도의 결과가 새 시도를 덮어쓸 수 있다).
     */
    public void attemptNow(Long taskId) {
        if (!enabled) {
            return;
        }
        String ownerToken = UUID.randomUUID().toString();
        store.claimSpecific(taskId, ownerToken).ifPresent(id -> store.processOne(id, ownerToken));
    }

    @Scheduled(initialDelayString = "${app.session-revocation.drain-initial-delay-ms:60000}",
            fixedDelayString = "${app.session-revocation.drain-interval-ms:120000}")
    public void drainScheduled() {
        if (!enabled) {
            return;
        }
        store.requeueStuck(LocalDateTime.now().minus(Duration.ofMillis(stuckAfterMs)));
        String ownerToken = UUID.randomUUID().toString();
        List<Long> ids = store.claimBatch(batchSize, ownerToken);
        for (Long id : ids) {
            store.processOne(id, ownerToken);
        }
    }

    /** 오래된 DONE/FAILED 행을 지운다. {@code enabled}와 무관하게 돈다(벌크 삭제라 엔티티를 로드하지 않아 rekey 창에도 안전). */
    @Scheduled(initialDelayString = "${app.session-revocation.retention-initial-delay-ms:120000}",
            fixedDelayString = "${app.session-revocation.retention-interval-ms:86400000}")
    public void cleanupOldTerminal() {
        if (!retentionEnabled) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minus(Duration.ofDays(retentionDays));
        long removed = store.deleteOldTerminal(threshold);
        if (removed > 0) {
            log.info("보관 기한이 지난 세션 폐기 태스크 {}건을 정리했습니다.", removed);
        }
    }
}
