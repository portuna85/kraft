package com.kraft.user.dto;

import com.kraft.user.domain.EmailPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 재설정 링크를 받을 주소. 가입 여부는 여기서 판단하지 않는다 — 응답이 갈리면
 * 그 자체로 계정 존재 여부를 확인하는 도구가 된다({@code PasswordResetService} 주석 참고).
 */
public record PasswordResetRequestDto(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = EmailPolicy.MAX_LENGTH, message = "이메일은 {max}자 이하로 입력하세요.")
        String email
) {
}
