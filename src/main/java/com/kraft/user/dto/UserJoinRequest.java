package com.kraft.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserJoinRequest {

    private String email;
    private String username;
    private String nickname;
    private String password;
}
