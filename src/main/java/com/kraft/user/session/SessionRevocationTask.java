package com.kraft.user.session;

import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.EmailAttributeConverter;
import com.kraft.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 비밀번호 변경·재설정·탈퇴 뒤 세션 폐기를 영속 태스크로 남긴다(B06).
 * <p>
 * 커밋 직후 {@code AfterCommit}이 곧바로 한 번 처리를 시도하지만(빠른 경로), 그 시도가
 * 실패하거나 시도 도중 프로세스가 죽으면 이 행이 DB에 남아 {@code SessionRevocationWorker}의
 * 주기 작업이 다시 집는다. {@code OutboxMail}과 같은 claim/재시도 구조를 그대로 쓴다.
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "session_revocation_tasks", indexes = {
        @Index(name = "IX_SESSION_REVOCATION_TASKS_STATUS_ID", columnList = "status, id"),
        @Index(name = "IX_SESSION_REVOCATION_TASKS_STATUS_UPDATED", columnList = "status, updated_at"),
})
public class SessionRevocationTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 태스크를 만든 시점(트랜잭션 커밋 시점)의 이메일. 탈퇴가 {@code users.email}을 익명
     * 주소로 바꿔도 이 값은 고정되어, 처리 시점에 다시 조회하지 않는다 — 그렇지 않으면 탈퇴
     * 후 같은 이메일로 재가입한 새 계정의 세션을 잘못 지울 수 있다. {@code users.email}과 같은
     * 방식(AES)으로 암호화해 저장한다.
     */
    @Column(name = "email_snapshot", nullable = false, length = 500)
    @Convert(converter = EmailAttributeConverter.class)
    private String emailSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionRevocationTaskStatus status;

    @Column(nullable = false)
    private int attempts;

    /** 마지막 실패 원인. 운영에서 왜 안 됐는지 확인할 근거다. */
    @Column(length = 500)
    private String lastError;

    private LocalDateTime completedAt;

    /** 어느 워커 인스턴스가 선점했는지. requeueStuck이 정체된 태스크를 되돌릴 때 함께 비운다. */
    @Column(name = "owner_token", length = 36)
    private String ownerToken;

    @Builder
    public SessionRevocationTask(User user, String emailSnapshot) {
        this.user = user;
        this.emailSnapshot = emailSnapshot;
        this.status = SessionRevocationTaskStatus.PENDING;
        this.attempts = 0;
    }

    public void markDone() {
        this.status = SessionRevocationTaskStatus.DONE;
        this.completedAt = LocalDateTime.now();
        this.lastError = null;
    }

    /** 남은 기회가 있으면 PENDING으로 돌려 다음 차례에 다시 집게 하고, 다 썼으면 FAILED로 끝낸다. */
    public void markFailed(String error, int maxAttempts) {
        this.status = attempts >= maxAttempts
                ? SessionRevocationTaskStatus.FAILED
                : SessionRevocationTaskStatus.PENDING;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }

    /** 정체 재큐잉 전용(B06). 이전 소유자의 지연된 처리가 새 소유자의 결과를 덮지 않게 한다. */
    public void releaseOwnership() {
        this.ownerToken = null;
    }
}
