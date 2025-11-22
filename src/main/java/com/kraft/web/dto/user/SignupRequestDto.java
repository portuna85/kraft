package com.kraft.web.dto.user;

import com.kraft.domain.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SignupRequestDto {

    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 4, max = 50, message = "이름은 4자 이상 50자 이하여야 합니다")
    private String name;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, max = 60, message = "비밀번호는 8자 이상 60자 이하여야 합니다")
    private String password;

    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String email;

    public User toEntity(String encodedPassword) {
        return User.of(name, encodedPassword, email);
    }
}

