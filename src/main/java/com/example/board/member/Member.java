package com.example.board.member;

import com.example.board.domain.BaseTimeEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(
        name = "members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"email"})
)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Email
    @NotBlank
    @Column(nullable = false, length = 120, unique = true) // unique 중복 지정해도 무방
    private String email;

    @NotBlank
    @Size(min = 2, max = 20)
    @Column(nullable = false, length = 20)
    private String username;

    @NotBlank
    @Column(nullable = false, length = 255) // BCrypt 60자지만 알고리즘 변경 여지 고려
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.USER;

    protected Member() {
        // JPA 기본 생성자
    }

    public Member(String email, String username, String passwordHash) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = Role.USER;
    }

    // ---- lifecycle: 값 정규화 ----
    @PrePersist
    @PreUpdate
    private void normalize() {
        if (email != null)   email = email.trim().toLowerCase(); // 케이스/공백 정리
        if (username != null) username = username.trim();
    }

    // ---- 도메인 메서드(필요한 변경만 노출) ----
    public void changeUsername(String newUsername) {
        if (newUsername == null || newUsername.isBlank())
            throw new IllegalArgumentException("username is blank");
        this.username = newUsername.trim();
    }

    public void changePasswordHash(String newHash) {
        if (newHash == null || newHash.isBlank())
            throw new IllegalArgumentException("password hash is blank");
        this.passwordHash = newHash;
    }

    public void promoteToAdmin() { this.role = Role.ADMIN; }
    public boolean isAdmin() { return this.role == Role.ADMIN; }

    // ---- getter ----
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }

    // ---- equals & hashCode ----
    // id가 존재할 때만 동등. 트랜지언트(새 객체)끼리는 동등하지 않음.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Member other = (Member) o;
        return id != null && id.equals(other.id);
    }
    @Override
    public int hashCode() {
        return (id != null ? id.hashCode() : 0);
    }


    public void setEmail(String email) {
        this.email = email;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    // 민감 정보 노출 방지
    @Override
    public String toString() {
        return "Member{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", username='" + username + '\'' +
                ", role=" + role +
                '}';
    }
}
