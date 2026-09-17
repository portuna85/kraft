package com.kraft.user.mail;

import com.kraft.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxMailRepository extends JpaRepository<OutboxMail, Long> {

    /**
     * PENDING 중 가장 오래된 것부터, 재시도 대기(backoff) 중이 아닌 것만 최대 {@code limit}개를
     * 행 잠금으로 선점한다. {@code SKIP LOCKED} 덕분에 동시에 도는 다른 선점(예약 실행 vs
     * {@code drainAsync})이 이미 잠근 행은 건너뛰고 그 다음 행을 집는다 — 같은 행을 두 번
     * 반환하지 않는다. 이 락은 호출한 트랜잭션이 커밋할 때까지 유지되므로, 뒤이은 상태
     * 변경(UPDATE)까지 같은 트랜잭션 안에서 끝내야 한다.
     */
    @Query(value = "SELECT id FROM outbox_mails WHERE status = 'PENDING' "
            + "AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP) "
            + "ORDER BY id ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Long> selectPendingIdsForUpdateSkipLocked(@Param("limit") int limit);

    /**
     * {@code updatedAt}을 직접 넘기는 것은 {@code @LastModifiedDate}가 엔티티 생명주기
     * 이벤트(PrePersist/PreUpdate)에서만 동작하고 벌크 JPQL UPDATE에는 관여하지 않기
     * 때문이다. {@code requeueStuck}이 이 값을 기준으로 정체 여부를 판단하므로 반드시
     * 함께 갱신해야 한다. {@code ownerToken}은 어느 워커 인스턴스가 집었는지 운영 로그로
     * 추적하기 위함이며 발송 로직은 이 값을 보지 않는다.
     */
    @Modifying
    @Query("UPDATE OutboxMail o SET o.status = com.kraft.user.mail.OutboxMailStatus.SENDING, "
            + "o.attempts = o.attempts + 1, o.updatedAt = :now, o.ownerToken = :ownerToken WHERE o.id IN :ids")
    int markSendingByIds(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now,
                          @Param("ownerToken") String ownerToken);

    /**
     * 지금도 이 {@code ownerToken}이 소유한 SENDING 행일 때만 값을 꺼낸다(B05). 정체
     * 재큐잉이 소유권을 비운 뒤에는 원래 워커가 이 id로 조회해도 빈 값을 받는다.
     */
    Optional<OutboxMail> findByIdAndOwnerTokenAndStatus(Long id, String ownerToken, OutboxMailStatus status);

    /** 지금도 이 {@code ownerToken}이 소유한 행일 때만 값을 꺼낸다(B05). markSent/markFailed가 쓴다. */
    Optional<OutboxMail> findByIdAndOwnerToken(Long id, String ownerToken);

    /**
     * 요청 제한에 쓴다 — 이 회원에게 이 종류의 메일을 마지막으로 만든 것이 언제인지 본다.
     * <p>
     * 종류를 함께 보는 것이 중요하다. 종류를 가리지 않으면 가입 직후 인증 메일을 받은 사람이
     * 곧바로 비밀번호 재설정을 요청했을 때 그 요청이 조용히 무시된다.
     */
    Optional<OutboxMail> findFirstByUserIdAndKindOrderByIdDesc(Long userId, OutboxMailKind kind);

    /**
     * SENDING인 채로 오래 남은 것을 다시 집을 수 있게 한다. 발송 도중 프로세스가 죽으면
     * 그 메일은 영영 PENDING으로 돌아오지 못한다.
     */
    List<OutboxMail> findByStatusAndUpdatedAtBefore(OutboxMailStatus status, LocalDateTime threshold);

    /** 상태 보고에 쓴다 — 대기·실패가 쌓이면 메일이 안 나가고 있다는 뜻이다. */
    long countByStatus(OutboxMailStatus status);

    /**
     * 탈퇴할 때 쓴다 — 없는 계정으로 갈 메일을 대기열에 남겨 둘 이유가 없다. 파생 삭제 대신
     * 한 문장으로 지운다(개선 보고서 "파생 delete 메서드의 엔티티별 삭제").
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM OutboxMail o WHERE o.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /** 보관 기한이 지난 종료 상태(SENT/FAILED) 행을 정리한다. PENDING/SENDING은 대상이 아니다. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM OutboxMail o WHERE o.status IN :statuses AND o.updatedAt < :threshold")
    long deleteByStatusInAndUpdatedAtBefore(@Param("statuses") List<OutboxMailStatus> statuses,
                                             @Param("threshold") LocalDateTime threshold);
}
