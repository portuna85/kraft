package com.kraft.user.dto;

import com.kraft.user.domain.User;
import com.kraft.user.domain.User.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinRequest {

    private String email;
    private String password;
    private String nickname;

    public User toEntity(String encodedPassword) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .role(Role.USER)
                .build();
    }
}
