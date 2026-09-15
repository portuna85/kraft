package com.kraft.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 회원 탈퇴 요청. 되돌릴 수 없는 작업이므로 현재 비밀번호를 한 번 더 확인한다 — 자리를 비운
 * 사이 남이 눌러 계정을 없애는 일도 이 확인이 막는다.
 */
public record WithdrawRequestDto(

        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword
) {
}
