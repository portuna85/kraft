package com.kraft.user.dto;

import com.kraft.user.domain.EmailPolicy;
import com.kraft.user.domain.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequestDto(

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하로 입력하세요.")
        // 제어 문자·폭 0 문자·방향 제어 문자(유니코드 Cc·Cf·Zl·Zp)는 눈에 보이지 않아 다른 사람 이름을 사칭하는 데
        // 쓰일 수 있다.
        @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*$", message = "이름에 보이지 않는 문자나 제어 문자를 쓸 수 없습니다.")
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
