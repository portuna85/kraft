package com.kraft.user.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 표시 이름의 보이지 않는 문자 거부(BE-47). */
class SignUpNameValidationTest {

    private static final String PASSWORD = "Aa!12345678";
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"관리​자", "관리자‮", "관리\u0000자", "관리 자", "﻿관리자"})
    void invisibleOrControlCharacters_areRejected(String name) {
        var violations = validator.validate(new SignUpRequestDto(name, "user@example.com", PASSWORD));

        assertThat(violations).extracting(v -> v.getMessage())
                .contains("이름에 보이지 않는 문자나 제어 문자를 쓸 수 없습니다.");
    }

    @Test
    void ordinaryNamesWithSpacesAndEmoji_areAccepted() {
        assertThat(validator.validate(new SignUpRequestDto("보노 보노 😀", "user@example.com", PASSWORD))).isEmpty();
    }
}
