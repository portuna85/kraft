package com.kraft.user.dto;

import com.kraft.user.domain.StrongPassword;
import jakarta.validation.constraints.NotBlank;
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
        @StrongPassword(label = "새 비밀번호")
        String newPassword
) {
}
