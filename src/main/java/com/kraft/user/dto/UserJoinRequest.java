package com.kraft.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserJoinRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    private String email;

    @NotBlank(message = "아이디는 필수입니다.")
    @Size(min = 3, max = 20, message = "아이디는 3~20자여야 합니다.")
    private String username;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 15, message = "닉네임은 2~15자여야 합니다.")
    private String nickname;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")
    private String password;
}
