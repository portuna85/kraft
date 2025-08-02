package com.kraft.user.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이메일: 유일, 필수
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, unique = true)
    private String username;

    // 닉네임: 유일, 필수
    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    // 비밀번호: 필수
    @Column(nullable = false)
    private String password;

    // 권한: USER / ADMIN
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    public enum Role {
        USER, ADMIN
    }

    // 권한 문자열 반환 (Spring Security 연동 시 사용 가능)
    public String getRoleKey() {
        return this.role.name();
    }
}
