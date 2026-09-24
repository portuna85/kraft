package com.kraft.user.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SessionRevocationTaskRepository extends JpaRepository<SessionRevocationTask, Long> {

    /**
     * PENDING 중 오래된 것부터 최대 {@code limit}개를 행 잠금으로 선점한다. {@code SKIP LOCKED}
     * 덕분에 동시에 도는 다른 선점(예약 실행 vs 커밋 직후 빠른 경로)이 이미 잠근 행은 건너뛴다.
     * {@code OutboxMailRepository.selectPendingIdsForUpdateSkipLocked}와 같은 구조다.
     */
    @Query(value = "SELECT id FROM session_revocation_tasks WHERE status = 'PENDING' "
            + "ORDER BY id ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Long> selectPendingIdsForUpdateSkipLocked(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE SessionRevocationTask t SET t.status = com.kraft.user.session.SessionRevocationTaskStatus.PROCESSING, "
            + "t.attempts = t.attempts + 1, t.updatedAt = :now, t.ownerToken = :ownerToken WHERE t.id IN :ids")
    int markProcessingByIds(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now,
                             @Param("ownerToken") String ownerToken);

    /**
     * 커밋 직후 빠른 경로 전용. 방금 만든 태스크 하나만, 아직 PENDING일 때만 선점한다 — 그 사이
     * 주기 작업이 먼저 집었다면(드물지만 가능) 0을 돌려주어 중복 처리를 피한다.
     */
    @Modifying
    @Query("UPDATE SessionRevocationTask t SET t.status = com.kraft.user.session.SessionRevocationTaskStatus.PROCESSING, "
            + "t.attempts = t.attempts + 1, t.updatedAt = :now, t.ownerToken = :ownerToken "
            + "WHERE t.id = :id AND t.status = com.kraft.user.session.SessionRevocationTaskStatus.PENDING")
    int markProcessingIfPending(@Param("id") Long id, @Param("now") LocalDateTime now,
                                 @Param("ownerToken") String ownerToken);

    /**
     * 지금도 이 {@code ownerToken}이 소유한 PROCESSING 행일 때만 값을 꺼낸다.
     * <p>
     * 비관적 쓰기 잠금을 잡아 {@link #findByStatusAndUpdatedAtBefore}(requeueStuck 전용,
     * 마찬가지로 잠근다)와 서로 배타적으로 돈다(개선 보고서 BE-20,
     * {@code OutboxMailRepository.findSendingByIdAndOwnerTokenForUpdate}와 같은 COR-03 패턴) —
     * 마침 처리 결과를 반영하는 도중인 행을 재큐잉이 동시에 건드리지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SessionRevocationTask> findByIdAndOwnerTokenAndStatus(
            Long id, String ownerToken, SessionRevocationTaskStatus status);

    /**
     * 처리 도중 프로세스가 죽으면 PROCESSING인 채로 오래 남는다. requeueStuck이 이 목록을
     * 배치로 나눠 집는다(B11) — {@code OutboxMailRepository.findByStatusAndUpdatedAtBefore}와
     * 같은 이유로 {@code Pageable}을 받는다. 대량 적체 시 전체를 한 번에 로딩하지 않는다.
     * <p>
     * 비관적 쓰기 잠금은 위 {@link #findByIdAndOwnerTokenAndStatus}(processOne 전용) 참고.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SessionRevocationTask> findByStatusAndUpdatedAtBefore(
            SessionRevocationTaskStatus status, LocalDateTime threshold, Pageable pageable);

    /** 관측용 집계(O03) — 재시도를 모두 소진해 사람이 봐야 하는 태스크 수. */
    long countByStatus(SessionRevocationTaskStatus status);

    /** 보관 기한이 지난 종료 상태(DONE/FAILED) 행을 지운다. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SessionRevocationTask t WHERE t.status IN :statuses AND t.updatedAt < :threshold")
    long deleteByStatusInAndUpdatedAtBefore(@Param("statuses") List<SessionRevocationTaskStatus> statuses,
                                             @Param("threshold") LocalDateTime threshold);
}
