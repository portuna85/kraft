package com.kraft.user.mail;

import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 보낼 메일을 DB에 적어 두는 대기열(아웃박스).
 * <p>
 * 예전에는 회원가입 트랜잭션 안에서 SMTP를 그대로 호출했다. {@code application.yml}의 연결·읽기·
 * 쓰기 타임아웃이 각각 5초라, 메일 서버가 굼뜨면 <b>DB 커넥션 하나를 최대 15초 붙잡은 채</b>
 * 기다렸다. 동시에 몇 명만 가입해도 커넥션 풀이 마르는 구조였다(개선 보고서 "메일 안정성").
 * <p>
 * 이제 트랜잭션 안에서는 이 행을 만들기만 하고, 실제 발송은 {@code OutboxMailWorker}가
 * <b>어떤 트랜잭션에도 속하지 않은 채</b> 수행한다. 덤으로 재시도와 발송 상태가 생긴다.
 * <p>
 * 수신 주소와 본문을 컬럼에 담지 않는 것은 의도다. 이메일은 {@code users.email}에 암호화해
 * 저장하는데 아웃박스에 평문으로 또 적으면 그 보호가 무의미해진다. 대신 회원과 토큰만 가리키고,
 * 보낼 때 그 자리에서 주소와 본문을 만든다.
 * <p>
 * 지금은 이메일 인증 메일만 담는다. 종류가 늘면 {@code type} 컬럼을 더하고 본문 생성을
 * 종류별로 나눈다.
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "outbox_mails")
public class OutboxMail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 본문에 실을 인증 토큰. 토큰 테이블의 행이 지워져도 이 값으로 링크를 만든다. */
    @Column(nullable = false, length = 100)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxMailStatus status;

    @Column(nullable = false)
    private int attempts;

    /** 마지막 실패 원인. 운영에서 왜 안 갔는지 확인할 근거다. */
    @Column(length = 500)
    private String lastError;

    private LocalDateTime sentAt;

    @Builder
    public OutboxMail(User user, String token) {
        this.user = user;
        this.token = token;
        this.status = OutboxMailStatus.PENDING;
        this.attempts = 0;
    }

    /** 발송을 시작한다. 다른 작업자가 같은 메일을 집지 못하게 상태를 먼저 바꾼다. */
    public void markSending() {
        this.status = OutboxMailStatus.SENDING;
        this.attempts += 1;
    }

    public void markSent() {
        this.status = OutboxMailStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.lastError = null;
    }

    /**
     * 실패를 기록한다. 남은 기회가 있으면 다시 PENDING으로 돌려 다음 차례에 집게 하고,
     * 다 썼으면 FAILED로 끝낸다.
     */
    public void markFailed(String error, int maxAttempts) {
        this.status = attempts >= maxAttempts ? OutboxMailStatus.FAILED : OutboxMailStatus.PENDING;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }
}
