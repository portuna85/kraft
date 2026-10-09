package com.kraft.user.mail;

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

public interface OutboxMailRepository extends JpaRepository<OutboxMail, Long> {

    /**
     * PENDING 중 오래된 것부터, 재시도 대기(backoff) 중이 아닌 것만 최대 {@code limit}개를 행 잠금으로 선점한다
     * ({@code SKIP LOCKED}라 동시 선점끼리 겹치지 않는다). 잠금은 커밋까지 유지되므로 뒤이은 UPDATE도 같은
     * 트랜잭션에서 끝낸다. {@code now}는 DB 시계가 아니라 자바에서 넘긴다 — {@code nextAttemptAt}이 자바 시각이라
     * 같은 시계로 비교해야 하고, DB 시간대가 다르면 백오프가 어긋난다.
     */
    @Query(value = "SELECT id FROM outbox_mails WHERE status = 'PENDING' "
            + "AND (next_attempt_at IS NULL OR next_attempt_at <= :now) "
            + "ORDER BY id ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Long> selectPendingIdsForUpdateSkipLocked(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 벌크 UPDATE에는 {@code @LastModifiedDate}가 안 먹으므로 {@code updatedAt}을 직접 갱신한다({@code requeueStuck}의 기준). */
    @Modifying
    @Query("UPDATE OutboxMail o SET o.status = com.kraft.user.mail.OutboxMailStatus.SENDING, "
            + "o.attempts = o.attempts + 1, o.updatedAt = :now, o.ownerToken = :ownerToken WHERE o.id IN :ids")
    int markSendingByIds(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now,
                          @Param("ownerToken") String ownerToken);

    /** 지금도 이 {@code ownerToken}이 소유한 SENDING 행일 때만 꺼낸다(재큐잉이 소유권을 비운 뒤에는 빈 값). */
    Optional<OutboxMail> findByIdAndOwnerTokenAndStatus(Long id, String ownerToken, OutboxMailStatus status);

    /**
     * markSent/markFailed 전용. ownerToken과 SENDING을 확인하며 비관적 쓰기 잠금을 잡는다 — 이 잠금이
     * {@link #findByStatusAndUpdatedAtBefore}(재큐잉)와 서로 배타적이라 "재큐잉하며 소유권 비움"과 "결과 반영"이
     * 반쯤 섞이지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboxMail o WHERE o.id = :id AND o.ownerToken = :ownerToken "
            + "AND o.status = com.kraft.user.mail.OutboxMailStatus.SENDING")
    Optional<OutboxMail> findSendingByIdAndOwnerTokenForUpdate(@Param("id") Long id,
                                                                @Param("ownerToken") String ownerToken);

    /** 이 회원에게 이 종류 메일을 마지막으로 만든 것(요청 제한용). 종류를 가려야 인증 메일 직후의 재설정 요청이 무시되지 않는다. */
    Optional<OutboxMail> findFirstByUserIdAndKindOrderByIdDesc(Long userId, OutboxMailKind kind);

    /**
     * 오래 SENDING으로 남은 행(발송 중 프로세스가 죽은 경우)을 {@code pageable}만큼 잠가 가져온다. 비관적 쓰기
     * 잠금이 {@link #findSendingByIdAndOwnerTokenForUpdate}와 서로 배타적이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxMail> findByStatusAndUpdatedAtBefore(OutboxMailStatus status, LocalDateTime threshold, Pageable pageable);

    /** 상태 보고에 쓴다 — 대기·실패가 쌓이면 메일이 안 나가고 있다는 뜻이다. */
    long countByStatus(OutboxMailStatus status);

    /** 탈퇴 시 없는 계정으로 갈 메일을 지운다(한 문장 벌크 삭제). */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM OutboxMail o WHERE o.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /** 보관 기한이 지난 종료 상태(SENT/FAILED) 행을 정리한다. PENDING/SENDING은 대상이 아니다. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM OutboxMail o WHERE o.status IN :statuses AND o.updatedAt < :threshold")
    long deleteByStatusInAndUpdatedAtBefore(@Param("statuses") List<OutboxMailStatus> statuses,
                                             @Param("threshold") LocalDateTime threshold);
}
