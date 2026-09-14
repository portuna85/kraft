package com.kraft.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그에 남길 이메일을 가리는 규칙. 경계를 고정해 두지 않으면 "가린 줄 알았는데 다 남는"
 * 경우가 생긴다 — 한 글자짜리 local part가 대표적이다.
 */
class EmailMaskerTest {

    @Test
    @DisplayName("사람을 가리키는 쪽은 첫 글자만 남기고 도메인은 그대로 둔다")
    void keepsOnlyTheFirstCharacterOfTheLocalPart() {
        assertThat(EmailMasker.mask("someone@example.com")).isEqualTo("s***@example.com");
    }

    /**
     * 첫 글자만 남기는 규칙을 한 글자 주소에 그대로 적용하면 <b>전부 남는다.</b>
     * 가장 놓치기 쉬운 경계라 따로 고정한다.
     */
    @Test
    @DisplayName("한 글자짜리 주소는 첫 글자도 남기지 않는다")
    void hidesSingleCharacterLocalPartEntirely() {
        assertThat(EmailMasker.mask("a@example.com")).isEqualTo("***@example.com");
        assertThat(EmailMasker.mask("ab@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    @DisplayName("@가 여럿이면 마지막 것을 기준으로 나눈다")
    void splitsOnTheLastAtSign() {
        assertThat(EmailMasker.mask("we..ird@quoted@example.com")).isEqualTo("w***@example.com");
    }

    /** 무엇이 들어 있는지 모르는 문자열을 로그에 흘리지 않는다. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "@example.com", "not-an-email"})
    @DisplayName("이메일 형태가 아니면 통째로 가린다")
    void hidesAnythingThatIsNotAnAddress(String value) {
        assertThat(EmailMasker.mask(value)).isEqualTo("***");
    }

    @Test
    @DisplayName("가린 결과에는 원래 주소가 남아 있지 않다")
    void maskedValueNeverContainsTheLocalPart() {
        String email = "very.identifying.name@example.com";

        assertThat(EmailMasker.mask(email))
                .doesNotContain("very.identifying.name")
                .doesNotContain(email);
    }
}
