package com.boardly.user.domain;


import com.boardly.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;


@Entity
@Table(name = "users")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class User extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @NotBlank @Column(nullable = false, unique = true, length = 30)
    private String username;


    @NotBlank @Column(nullable = false)
    private String password;


    @NotBlank @Column(nullable = false, length = 30)
    private String nickname;


    @Email @NotBlank @Column(nullable = false, unique = true)
    private String email;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;
}