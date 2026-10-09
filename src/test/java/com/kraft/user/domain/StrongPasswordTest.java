package com.kraft.user.domain;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 세 DTO가 공유하는 비밀번호 형식 제약. */
class StrongPasswordTest {

    private record Plain(@StrongPassword String password) {
    }

    private record Labeled(@StrongPassword(label = "새 비밀번호") String password) {
    }

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("규칙을 지키면 통과한다")
    void valid_passes() {
        assertThat(validator.validate(new Plain("Abcdef1!"))).isEmpty();
    }

    @Test
    @DisplayName("null·빈 값은 통과시킨다 — 필수 여부는 @NotBlank가 본다")
    void nullAndEmpty_pass() {
        assertThat(validator.validate(new Plain(null))).isEmpty();
        assertThat(validator.validate(new Plain(""))).isEmpty();
    }

    @Test
    @DisplayName("대문자·소문자·특수문자 중 하나라도 없으면 구성 규칙 메시지를 낸다")
    void missingCharacterClass_reportsCompositionMessage() {
        assertThat(validator.validate(new Plain("abcdefg1!")))
                .extracting(v -> v.getMessage())
                .containsExactly("비밀번호는 대문자, 소문자, 특수문자를 각각 1자 이상 포함해야 합니다.");
        assertThat(validator.validate(new Plain("ABCDEFG1!")))
                .extracting(v -> v.getMessage())
                .containsExactly("비밀번호는 대문자, 소문자, 특수문자를 각각 1자 이상 포함해야 합니다.");
        assertThat(validator.validate(new Plain("Abcdefg12")))
                .extracting(v -> v.getMessage())
                .containsExactly("비밀번호는 대문자, 소문자, 특수문자를 각각 1자 이상 포함해야 합니다.");
    }

    @Test
    @DisplayName("너무 짧거나 길면 길이 메시지를 내고, 주어는 label을 따른다")
    void badLength_reportsLengthMessageWithLabel() {
        assertThat(validator.validate(new Labeled("Ab1!")))
                .extracting(v -> v.getMessage())
                .containsExactly("새 비밀번호는 8자 이상 72자 이하여야 합니다.");
        assertThat(validator.validate(new Plain("Aa!" + "a".repeat(70))))
                .extracting(v -> v.getMessage())
                .containsExactly("비밀번호는 8자 이상 72자 이하여야 합니다.");
    }

    @Test
    @DisplayName("길이와 구성이 모두 틀리면 두 메시지를 함께 낸다(예전 @Size + @Pattern과 같다)")
    void bothWrong_reportsBothMessages() {
        assertThat(validator.validate(new Plain("abc")))
                .extracting(v -> v.getMessage())
                .containsExactlyInAnyOrder(
                        "비밀번호는 8자 이상 72자 이하여야 합니다.",
                        "비밀번호는 대문자, 소문자, 특수문자를 각각 1자 이상 포함해야 합니다.");
    }
}
