package com.kraft.user.dto;

import com.kraft.user.domain.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequestDto(

        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @StrongPassword(label = "새 비밀번호")
        String newPassword
) {
}
