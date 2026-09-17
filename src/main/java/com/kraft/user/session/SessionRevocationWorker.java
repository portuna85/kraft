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
 * 세션 폐기 태스크를 실제로 처리한다(B06).
 * <p>
 * 비밀번호 변경·재설정·탈퇴 커밋 직후 {@code AfterCommit}에서 {@link #attemptNow}로 한 번
 * 곧바로 시도한다(빠른 경로) — 대부분의 요청은 여기서 끝난다. 그 시도가 실패하거나(세션
 * 저장소 장애 등) 시도 도중 프로세스가 죽으면, DB 변경과 달리 세션 폐기는 유실될 수 있었다.
 * 태스크가 DB에 남아 있으므로 {@link #drainScheduled}가 주기적으로 다시 집어 최종적으로
 * 완수한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SessionRevocationWorker {

    private final SessionRevocationStore store;

    /** 이 인스턴스가 집은 태스크임을 구분하기 위한 값. 처리 로직은 소유권 확인에만 쓴다. */
    private final String ownerToken = UUID.randomUUID().toString();

    @Value("${app.session-revocation.batch-size:20}")
    private int batchSize;

    /** 이 시간 넘게 PROCESSING이면 처리 도중 중단된 것으로 보고 되돌린다. */
    @Value("${app.session-revocation.stuck-after-ms:300000}")
    private long stuckAfterMs;

    /** 종료 상태(DONE/FAILED) 행을 이 기간이 지나면 지운다. */
    @Value("${app.session-revocation.retention-days:30}")
    private int retentionDays;

    /**
     * 커밋 직후 곧바로 한 번 시도한다. 방금 만든 태스크 하나만 처리하므로 응답 지연은 세션
     * 폐기 자체(빠른 DB 작업)만큼만 늘어난다. 실패해도 태스크는 이미 커밋되어 있으므로
     * {@link #drainScheduled}가 이어받는다.
     */
    public void attemptNow(Long taskId) {
        store.claimSpecific(taskId, ownerToken).ifPresent(id -> store.processOne(id, ownerToken));
    }

    @Scheduled(initialDelayString = "${app.session-revocation.drain-initial-delay-ms:60000}",
            fixedDelayString = "${app.session-revocation.drain-interval-ms:120000}")
    public void drainScheduled() {
        store.requeueStuck(LocalDateTime.now().minus(Duration.ofMillis(stuckAfterMs)));
        List<Long> ids = store.claimBatch(batchSize, ownerToken);
        for (Long id : ids) {
            store.processOne(id, ownerToken);
        }
    }

    /**
     * 종료된 지 오래된 DONE/FAILED 행을 지운다. 처리 자체와는 다른 관심사이므로 항상 돈다.
     */
    @Scheduled(initialDelayString = "${app.session-revocation.retention-initial-delay-ms:120000}",
            fixedDelayString = "${app.session-revocation.retention-interval-ms:86400000}")
    public void cleanupOldTerminal() {
        LocalDateTime threshold = LocalDateTime.now().minus(Duration.ofDays(retentionDays));
        long removed = store.deleteOldTerminal(threshold);
        if (removed > 0) {
            log.info("보관 기한이 지난 세션 폐기 태스크 {}건을 정리했습니다.", removed);
        }
    }
}
