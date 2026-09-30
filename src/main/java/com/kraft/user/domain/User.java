package com.kraft.user.domain;

import com.kraft.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 회원 정보
 * 회원정보 변경은 비밀번호 변경만 가능하다.
 * 회원 이메일 정보 초기 회원가입시 Role 손님 이메일 인증시 일반사용자
 * 일반사용자는 Post, Comment의 작성, 수정, 삭제가 가능하다.
 * 모든 사용자는 Post의 조회가 가능하다.
 *
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "UK_USER_EMAIL_HMAC", columnNames = "email_hmac"),
        @UniqueConstraint(name = "UK_USER_NAME", columnNames = "name")
}, indexes = {
        // V11__user_suspension.sql. 엔티티에 선언이 없어 ddl-auto: update로 만든 기존 DB에는
        // 이 인덱스가 생기지 않았다(개선 보고서 O01).
        @Index(name = "IX_USERS_SUSPENDED_UNTIL", columnList = "suspended_until"),
        // V9__user_withdrawal.sql의 IX_USERS_WITHDRAWN_AT(withdrawn_at)는 V25에서 지웠다 —
        // withdrawnAt을 거르는 쿼리(UserRepository.findGuestsMissingVerificationMail)가 실제로는
        // role=GUEST를 선두 조건으로 쓰는 IX_USERS_ROLE_CREATED_AT을 타고, 이 컬럼 단독으로
        // 쓰는 쿼리는 없었다(개선 보고서 BE-11).
        // GuestVerificationSweeper가 role=GUEST AND created_at < 임계값으로 훑는다(V23, 개선
        // 보고서 PERF-05).
        @Index(name = "IX_USERS_ROLE_CREATED_AT", columnList = "role, created_at"),
})
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회원 계정정보. 표시 이름이자 중복 금지 대상이다 — UserService의 사전 검사만으로는
    // 검사와 INSERT 사이에 같은 이름이 들어올 수 있어, DB 유니크 제약이 최종 경계다.
    @Column(nullable = false, length = 50)
    private String name;

    // AES로 암호화해 저장한다(EmailAttributeConverter). 조회는 이 컬럼이 아니라 emailHmac으로 한다.
    @Column(nullable = false, length = 500)
    @Convert(converter = EmailAttributeConverter.class)
    private String email;

    // email의 HMAC-SHA256(pepper). 조회·중복확인·유니크 제약은 전부 이 컬럼을 통해 이뤄진다(EmailHasher).
    // 키 없는 해시와 달리 DB만 유출돼서는 후보 주소 목록으로 가입 여부를 확인할 수 없다(P0-3).
    @Column(name = "email_hmac", nullable = false, length = 64)
    private String emailHmac;

    @Column(nullable = false, length = 100)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * 탈퇴한 시각. null이면 쓰고 있는 계정이다.
     * <p>
     * 행을 지우지 않는 이유는 글·댓글이 이 회원을 참조하기 때문이다(V9 주석 참고). 대신
     * {@link #withdraw}가 이름·이메일·비밀번호를 쓸 수 없는 값으로 덮어쓴다.
     */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    /**
     * 정지가 풀리는 시각. null이거나 이미 지났으면 정지 중이 아니다.
     * <p>
     * 기간을 시각으로 두고 매번 현재 시각과 비교한다 — 해제 배치가 필요 없고, 배치가 멈춰서
     * 정지가 안 풀리는 일도 없다.
     */
    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    /** 정지 사유. 정지된 사람에게 그대로 보여준다. */
    @Column(name = "suspension_reason", length = 200)
    private String suspensionReason;

    /**
     * 로그인 연속 실패 횟수(A-SEC-08, V29). 성공하면 0으로 돌아간다. 정지({@link #suspendedUntil})와는
     * 다른 축이다 — 정지는 글쓰기만 막고 로그인은 그대로 두지만, 이건 로그인 자체를 막는다.
     */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    /** 로그인이 잠기는 시각. null이거나 이미 지났으면 잠긴 게 아니다({@link #isSuspended}와 같은 패턴). */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * 비밀번호 변경·정지·탈퇴가 같은 행을 동시에 바꿀 때 나중에 flush되는 쪽이 앞선 변경을
     * 조용히 덮어쓰지 않도록 한다(B07). {@code PostImage.version}과 같은 목적이다.
     */
    @Version
    private long version;

    @Builder
    public User(String name, String email, String password, Role role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public String getRoleKey() {
        return this.role.getKey();
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void promoteToUser() {
        this.role = Role.USER;
    }

    /**
     * 탈퇴 처리. 남는 것은 "이 글을 누군가 썼다"는 연결뿐이고, 그 사람을 가리키는 값은 모두
     * 사라진다. 이메일이 바뀌면 {@link #hashEmail}이 email_hmac도 다시 계산하므로 원래 주소로
     * 다시 가입할 수 있다.
     *
     * @param placeholderEmail 탈퇴 계정을 가리키는 쓰지 않는 주소
     * @param placeholderName  화면에 보일 익명 이름(닉네임은 유니크라 서로 달라야 한다)
     * @param unusablePassword 아무도 맞힐 수 없는 인코딩된 비밀번호
     */
    public void withdraw(String placeholderEmail, String placeholderName, String unusablePassword) {
        this.email = placeholderEmail;
        this.name = placeholderName;
        this.password = unusablePassword;
        this.withdrawnAt = LocalDateTime.now();
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    /** 이 시각까지 글·댓글을 쓸 수 없게 한다. 읽기와 로그인은 그대로 둔다. */
    public void suspendUntil(LocalDateTime until, String reason) {
        this.suspendedUntil = until;
        this.suspensionReason = reason;
    }

    /** 기간이 남았는지 지금 판정한다. 만료된 정지는 아무것도 하지 않아도 저절로 풀린다. */
    public boolean isSuspended() {
        return suspendedUntil != null && LocalDateTime.now().isBefore(suspendedUntil);
    }

    /** 관리자가 기간을 다 채우기 전에 푼다. 사유는 기록에서 지우지 않는다. */
    public void liftSuspension() {
        this.suspendedUntil = null;
    }

    /** 로그인 실패 한 번을 누적하고, 누적된 값을 돌려준다(잠금 여부는 호출한 쪽이 판단한다). */
    public int recordFailedLogin() {
        this.failedLoginAttempts++;
        return this.failedLoginAttempts;
    }

    /** 지금부터 {@code duration} 동안 로그인을 막는다. */
    public void lockFor(Duration duration) {
        this.lockedUntil = LocalDateTime.now().plus(duration);
    }

    /** 기간이 남았는지 지금 판정한다. 만료된 잠금은 아무것도 하지 않아도 저절로 풀린다. */
    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    /** 로그인 성공 시 누적을 지운다. */
    public void resetFailedLogins() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    @PrePersist
    @PreUpdate
    private void hashEmail() {
        this.emailHmac = EmailHasher.hmacHex(this.email);
    }
}
