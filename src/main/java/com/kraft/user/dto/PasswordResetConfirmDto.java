package com.kraft.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 메일로 받은 토큰과 새 비밀번호. 비밀번호 규칙은 가입·비밀번호 변경과 같아야 한다 —
 * 재설정 경로로만 약한 비밀번호가 들어오면 규칙이 있으나 마나다.
 */
public record PasswordResetConfirmDto(

        @NotBlank(message = "재설정 링크가 올바르지 않습니다.")
        @Size(max = 100, message = "재설정 링크가 올바르지 않습니다.")
        String token,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, message = "새 비밀번호는 8자 이상이어야 합니다.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9]).*$",
                message = "새 비밀번호는 대문자, 소문자, 특수문자를 각각 1자 이상 포함해야 합니다."
        )
        String newPassword
) {
}
