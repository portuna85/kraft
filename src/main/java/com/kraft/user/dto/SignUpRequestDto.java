package com.kraft.user.dto;

import com.kraft.user.domain.EmailPolicy;
import com.kraft.user.domain.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequestDto(

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하로 입력하세요.")
        String name,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = EmailPolicy.MAX_LENGTH, message = "이메일은 {max}자 이하로 입력하세요.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @StrongPassword
        String password
) {
}
