package com.kraft.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EmailHasher#sha512Hex}의 출력 계약을 고정한다. 예전에는 이 메서드를 직접 테스트하지
 * 않고 {@code UserRepositoryTest} 등에서 조회 키를 만드는 용도로만 간접 사용했다.
 * {@code String.format} 반복을 {@link java.util.HexFormat}로 바꾸면서(개선 보고서 "해시
 * 문자열 생성의 반복 포맷 비용") 출력 형태(소문자·128자·구분자 없음)가 그대로인지 알려진
 * SHA-512 테스트 벡터로 고정해 둔다.
 */
class EmailHasherTest {

    @Test
    void sha512Hex_ofEmptyString_matchesKnownTestVector() {
        // SHA-512("") — 공개된 표준 테스트 벡터.
        assertThat(EmailHasher.sha512Hex("")).isEqualTo(
                "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e");
    }

    @Test
    void sha512Hex_returnsLowercase128CharacterHex() {
        String hash = EmailHasher.sha512Hex("tester@example.com");

        assertThat(hash).hasSize(128);
        assertThat(hash).isEqualTo(hash.toLowerCase());
        assertThat(hash).matches("[0-9a-f]{128}");
    }

    @Test
    void sha512Hex_isDeterministicForTheSameInput() {
        assertThat(EmailHasher.sha512Hex("same@example.com"))
                .isEqualTo(EmailHasher.sha512Hex("same@example.com"));
    }

    @Test
    void sha512Hex_differsForDifferentInput() {
        assertThat(EmailHasher.sha512Hex("a@example.com"))
                .isNotEqualTo(EmailHasher.sha512Hex("b@example.com"));
    }
}
