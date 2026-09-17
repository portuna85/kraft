package com.kraft.user.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 새 비밀번호에 최대 길이(72자)를 추가한 것을 검증한다(개선 보고서 "비밀번호 공백 처리
 * 불일치와 길이 정책"). 이 검증은 문자 수만 본다 — 실제로 설치된 인코더는 72
 * <b>바이트</b> 기준이라, 한글·이모지 등 멀티바이트 문자가 섞이면 이 검증을 통과한 72자
 * 입력도 실제 가입 시점에는 인코더가 거절할 수 있다(조용한 절단이 아니라 예외로 거절함 —
 * {@code PasswordMultibyteBoundaryTest} 참고). 이 불일치는 개선 보고서 B12로 남겨두고
 * 이번 검증에서는 고치지 않는다.
 */
@SpringBootTest
class PasswordLengthPolicyTest {

    private static final String VALID_PASSWORD_72 = "Aa!" + "a".repeat(69); // 72자, 규칙 통과

    @Autowired
    private Validator validator;

    @Test
    void signUp_password73Characters_isRejected() {
        var dto = new SignUpRequestDto("이름", "user@example.com", VALID_PASSWORD_72 + "a");

        assertThat(validator.validate(dto))
                .extracting(v -> v.getMessage())
                .contains("비밀번호는 8자 이상 72자 이하여야 합니다.");
    }

    @Test
    void signUp_password72Characters_passesLengthValidation() {
        var dto = new SignUpRequestDto("이름", "user@example.com", VALID_PASSWORD_72);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void changePassword_newPassword73Characters_isRejected() {
        var dto = new ChangePasswordRequestDto("currentAny", VALID_PASSWORD_72 + "a");

        assertThat(validator.validate(dto))
                .extracting(v -> v.getMessage())
                .contains("새 비밀번호는 8자 이상 72자 이하여야 합니다.");
    }

    @Test
    void passwordResetConfirm_newPassword73Characters_isRejected() {
        var dto = new PasswordResetConfirmDto("t".repeat(20), VALID_PASSWORD_72 + "a");

        assertThat(validator.validate(dto))
                .extracting(v -> v.getMessage())
                .contains("새 비밀번호는 8자 이상 72자 이하여야 합니다.");
    }
}
