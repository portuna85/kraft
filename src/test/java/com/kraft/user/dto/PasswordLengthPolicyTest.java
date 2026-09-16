package com.kraft.user.dto;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 새 비밀번호에 최대 길이(72자)를 추가한 것을 검증한다(개선 보고서 "비밀번호 공백 처리
 * 불일치와 길이 정책"). BCrypt는 72바이트를 넘는 입력을 조용히 잘라 버리므로, 검증 없이
 * 그보다 긴 비밀번호를 받으면 "저장한 값"과 "사용자가 입력한 값"이 갈린다.
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
