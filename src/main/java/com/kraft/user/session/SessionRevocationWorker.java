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

    /**
     * {@code false}면 예약 실행과 {@link #attemptNow}(커밋 직후 빠른 경로) 모두 막는다.
     * 이메일 키 교체(rekey) 창에서 이 워커가 옛 키로 암호화된 {@code email_snapshot}을
     * 새 키로 복호화하려다 죽는 사고를 막기 위해 도입했다(O02) — {@code application-rekey.yml}이
     * 이 플래그를 끈다.
     */
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

    /**
     * {@code cleanupOldTerminal}만의 별도 스위치(개선 보고서 OBS-05). {@code enabled}와 무관하게
     * 항상 돌게 만든 것은 의도한 설계이지만({@link #cleanupOldTerminal} 참고), 그 사실이
     * {@code app.session-revocation.enabled: false}만 보고 "이 워커의 예약 작업을 전부 껐다"고
     * 오해하기 쉽게 만든다. 정리만 따로 끄고 싶을 때 이 플래그를 쓴다.
     */
    @Value("${app.session-revocation.retention-enabled:true}")
    private boolean retentionEnabled = true;

    /**
     * 커밋 직후 곧바로 한 번 시도한다. 방금 만든 태스크 하나만 처리하므로 응답 지연은 세션
     * 폐기 자체(빠른 DB 작업)만큼만 늘어난다. 실패해도 태스크는 이미 커밋되어 있으므로
     * {@link #drainScheduled}가 이어받는다.
     * <p>
     * 동기로 유지한다(비동기 디스패치는 BE-09 검토 중 시도했다가 되돌렸다) — 비밀번호 변경·
     * 재설정·탈퇴 응답이 세션이 실제로 끊긴 뒤에 돌아온다는 것이 F04의 핵심 보장이다.
     * {@code claimSpecific}/{@code processOne}이 여는 {@code REQUIRES_NEW} 커넥션은 이
     * 메서드가 실행되는 짧은 시간만 추가로 물린다.
     * <p>
     * 소유 토큰은 호출마다 새로 만든다(개선 보고서 BE-20, {@code OutboxMailWorker.drain}과 같은
     * COR-03 패턴) — 이 워커 인스턴스가 생성될 때 만든 토큰 하나를 모든 호출이 공유하면, 정체
     * 재큐잉이 어떤 행의 소유권을 비운 뒤 같은 인스턴스가 곧바로 그 행을 다시 집을 때 새 시도도
     * 똑같은 토큰을 쓰게 되어, 뒤늦게 도착한 이전 시도의 결과가 "지금도 내 토큰"으로 오인되어
     * 새 시도를 덮어쓸 수 있었다.
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

    /**
     * 종료된 지 오래된 DONE/FAILED 행을 지운다. 처리 자체와는 다른 관심사이므로
     * {@code enabled} 플래그와 무관하게 항상 돈다 — 상태·시각 기준 bulk delete라 엔티티를
     * 로드하지 않으므로 rekey 창에도 안전하다. {@code retentionEnabled}로만 따로 끌 수
     * 있다(OBS-05).
     */
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
