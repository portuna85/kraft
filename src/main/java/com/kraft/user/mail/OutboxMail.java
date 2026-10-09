package com.kraft.user.mail;

import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 보낼 메일을 DB에 적어 두는 대기열(아웃박스). 트랜잭션 안에서는 이 행만 만들고, 실제 발송은
 * {@code OutboxMailWorker}가 트랜잭션 밖에서 한다(재시도·상태 포함). 수신 주소와 본문은 컬럼에 담지 않는다 —
 * 암호화해 둔 {@code users.email}을 평문으로 또 적으면 보호가 무의미해지므로, 회원과 토큰만 가리키고 보낼 때
 * 만든다. 종류별 본문은 {@link OutboxMailKind}로 구분한다.
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "outbox_mails", indexes = {
        @Index(name = "IX_OUTBOX_MAILS_STATUS_ID", columnList = "status, id"), // V7
        @Index(name = "IX_OUTBOX_MAILS_STATUS_UPDATED", columnList = "status, updated_at"), // V7
        @Index(name = "IX_OUTBOX_MAILS_USER_KIND", columnList = "user_id, kind, id"), // V14
})
public class OutboxMail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 본문에 실을 인증 토큰(평문). 발송이 끝나면(SENT/FAILED) 비워 보관 기간 동안 평문이 남지 않게 하고, 재시도(PENDING)에는 유지한다. */
    @Column(length = 100)
    private String token;

    /** 무엇을 보내려던 행인지. 제목·본문은 보낼 때 이 값으로 만든다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxMailKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxMailStatus status;

    @Column(nullable = false)
    private int attempts;

    /** 마지막 실패 원인. 운영에서 왜 안 갔는지 확인할 근거다. */
    @Column(length = 500)
    private String lastError;

    private LocalDateTime sentAt;

    /** 다음 재시도가 가능한 시각(지수 백오프). null이면 즉시 재시도 대상(새 메일)이다. */
    private LocalDateTime nextAttemptAt;

    /** 선점한 워커의 임대 토큰. 결과 반영 시 소유권 확인에 쓴다. */
    @Column(length = 36)
    private String ownerToken;

    @Builder
    public OutboxMail(User user, String token, OutboxMailKind kind) {
        this.user = user;
        this.token = token;
        this.kind = kind;
        this.status = OutboxMailStatus.PENDING;
        this.attempts = 0;
    }

    /** 발송을 시작한다. 다른 작업자가 같은 메일을 집지 못하게 상태를 먼저 바꾼다. */
    public void markSending() {
        this.status = OutboxMailStatus.SENDING;
        this.attempts += 1;
        this.nextAttemptAt = null;
    }

    public void markSent() {
        this.status = OutboxMailStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.lastError = null;
        this.nextAttemptAt = null;
        this.token = null;
    }

    /** 실패를 기록한다. 기회가 남으면 PENDING으로 돌리고 {@code 2^attempts}분(최대 60분) 뒤로 미루며, 다 쓰면 FAILED로 끝낸다. */
    public void markFailed(String error, int maxAttempts) {
        this.status = attempts >= maxAttempts ? OutboxMailStatus.FAILED : OutboxMailStatus.PENDING;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        this.nextAttemptAt = this.status == OutboxMailStatus.PENDING
                ? LocalDateTime.now().plusMinutes(backoffMinutes())
                : null;
        if (this.status == OutboxMailStatus.FAILED) {
            this.token = null;
        }
    }

    /** 발송 직전 토큰이 재발급되어 더는 유효하지 않을 때 즉시 종료한다. 재시도 대상이 아니다. */
    public void markStale(String reason) {
        this.status = OutboxMailStatus.FAILED;
        this.lastError = reason;
        this.nextAttemptAt = null;
        this.token = null;
    }

    /** 정체 재큐잉 전용. 소유권을 비워, 뒤늦게 온 이전 소유자의 markSent/markFailed가 새 소유자의 처리를 덮지 못하게 한다. */
    public void releaseOwnership() {
        this.ownerToken = null;
    }

    private long backoffMinutes() {
        return Math.min(60, 1L << Math.min(attempts, 6));
    }
}
