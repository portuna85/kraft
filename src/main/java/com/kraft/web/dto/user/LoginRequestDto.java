package com.kraft.web.dto.user;

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
public class LoginRequestDto {

    @NotBlank(message = "이름은 필수입니다")
    private String name;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, max = 60, message = "비밀번호는 8자 이상 60자 이하여야 합니다")
    private String password;
}
