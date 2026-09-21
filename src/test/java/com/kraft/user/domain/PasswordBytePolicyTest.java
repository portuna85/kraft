package com.kraft.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link PasswordBytePolicy}의 UTF-8 바이트 경계 판정(B15). */
class PasswordBytePolicyTest {

    @Test
    @DisplayName("72바이트 이하(ASCII)는 통과한다")
    void validate_asciiWithin72Bytes_passes() {
        String password = "a".repeat(72);

        assertThatCode(() -> PasswordBytePolicy.validate(password)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("73바이트(ASCII)는 거절한다")
    void validate_asciiOver72Bytes_throws() {
        String password = "a".repeat(73);

        assertThatThrownBy(() -> PasswordBytePolicy.validate(password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72바이트");
    }

    @Test
    @DisplayName("한글 24자(72바이트)는 통과하지만 25자(75바이트)는 거절한다")
    void validate_koreanBoundary() {
        assertThatCode(() -> PasswordBytePolicy.validate("가".repeat(24))).doesNotThrowAnyException();

        assertThatThrownBy(() -> PasswordBytePolicy.validate("가".repeat(25)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null은 통과한다 — 필수 여부는 DTO의 @NotBlank가 담당한다")
    void validate_null_passes() {
        assertThatCode(() -> PasswordBytePolicy.validate(null)).doesNotThrowAnyException();
    }
}
