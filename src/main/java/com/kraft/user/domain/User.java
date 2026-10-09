package com.kraft.user.domain;

import com.kraft.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 회원. 회원정보 변경은 비밀번호 변경만 가능하다. 가입 직후 Role은 GUEST, 이메일 인증 뒤 USER가 된다. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "UK_USER_EMAIL_HMAC", columnNames = "email_hmac"),
        @UniqueConstraint(name = "UK_USER_NAME", columnNames = "name")
}, indexes = {
        // GuestVerificationSweeper가 role=GUEST AND created_at < 임계값으로 훑는다(V23).
        @Index(name = "IX_USERS_ROLE_CREATED_AT", columnList = "role, created_at"),
})
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 표시 이름이자 중복 금지 대상 — 사전 검사와 INSERT 사이 경쟁은 DB 유니크 제약이 막는다.
    @Column(nullable = false, length = 50)
    private String name;

    // AES로 암호화해 저장한다(EmailAttributeConverter). 조회는 이 컬럼이 아니라 emailHmac으로 한다.
    @Column(nullable = false, length = 500)
    @Convert(converter = EmailAttributeConverter.class)
    private String email;

    // email의 HMAC-SHA256(pepper). 조회·중복확인·유니크 제약은 이 컬럼으로 한다(EmailHasher).
    // 키 없는 해시와 달리 DB 유출만으로는 후보 주소로 가입 여부를 확인할 수 없다.
    @Column(name = "email_hmac", nullable = false, length = 64)
    private String emailHmac;

    @Column(nullable = false, length = 100)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** 탈퇴한 시각(null이면 사용 중). 글·댓글이 참조하므로 행을 지우지 않고 {@link #withdraw}가 이름·이메일·비밀번호를 쓸 수 없는 값으로 덮어쓴다. */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    /** 로그인 연속 실패 횟수(성공하면 0). */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    /** 로그인이 잠기는 시각. null이거나 이미 지났으면 잠긴 게 아니다(시각과 비교해 저절로 풀린다). */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** 비밀번호 변경·탈퇴가 같은 행을 동시에 바꿀 때 나중 flush가 앞선 변경을 덮어쓰지 않게 한다. */
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
     * 탈퇴 처리: 그 사람을 가리키는 값은 모두 사라지고 "이 글을 누군가 썼다"는 연결만 남는다. 이메일이 바뀌면
     * {@link #hashEmail}이 email_hmac도 다시 계산해 원래 주소로 재가입할 수 있다.
     *
     * @param placeholderName 익명 이름(닉네임은 유니크라 서로 달라야 한다)
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

    /** 기간이 남았는지 지금 판정한다. 만료된 잠금은 아무것도 하지 않아도 저절로 풀린다. */
    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    @PrePersist
    @PreUpdate
    private void hashEmail() {
        this.emailHmac = EmailHasher.hmacHex(this.email);
    }
}
