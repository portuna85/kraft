package com.kraft.user.domain;

import com.kraft.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
        @UniqueConstraint(name = "UK_USER_EMAIL_HASH", columnNames = "email_hash"),
        @UniqueConstraint(name = "UK_USER_NAME", columnNames = "name")
})
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회원 계정정보. 표시 이름이자 중복 금지 대상이다 — UserService의 사전 검사만으로는
    // 검사와 INSERT 사이에 같은 이름이 들어올 수 있어, DB 유니크 제약이 최종 경계다.
    @Column(nullable = false, length = 50)
    private String name;

    // AES로 암호화해 저장한다(EmailAttributeConverter). 조회는 이 컬럼이 아니라 emailHash로 한다.
    @Column(nullable = false, length = 500)
    @Convert(converter = EmailAttributeConverter.class)
    private String email;

    // email의 SHA-512 해시. 조회·중복확인·유니크 제약은 전부 이 컬럼을 통해 이뤄진다(EmailHasher).
    @Column(name = "email_hash", nullable = false, length = 128)
    private String emailHash;

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
     * 사라진다. 이메일이 바뀌면 {@link #hashEmail}이 email_hash도 다시 계산하므로 원래 주소로
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

    @PrePersist
    @PreUpdate
    private void hashEmail() {
        this.emailHash = EmailHasher.sha512Hex(this.email);
    }
}
