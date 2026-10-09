package com.kraft.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link EmailHasher#sha512Hex}의 출력 계약을 고정한다. {@code String.format} 반복을 {@link java.util.HexFormat}으로 바꿔도 출력 형태(소문자·128자·구분자 없음)가 그대로인지 알려진 SHA-512 테스트 벡터로 확인한다. */
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

    /** 정적 pepper는 JVM 전체가 공유하므로 다른 테스트가 보는 값으로 되돌려 둔다. */
    private static final String SHARED_TEST_PEPPER = "test-only-hash-pepper-0123456789";

    @org.junit.jupiter.api.AfterEach
    void restorePepper() {
        EmailHasher.configurePepper(SHARED_TEST_PEPPER);
    }

    @Test
    void hmacHex_matchesIndependentlyComputedVector() {
        EmailHasher.configurePepper("test-pepper-0123456789");

        // python: hmac.new(b"test-pepper-0123456789", b"user@example.com", sha256).hexdigest()
        assertThat(EmailHasher.hmacHex("user@example.com")).isEqualTo(
                "873614aece2f7f7975bf314459cf9d74d407ed1f74704a9efe47727c0bb21765");
    }

    @Test
    void hmacHex_dependsOnPepper_andIsLowercase64Hex() {
        EmailHasher.configurePepper("pepper-one-0123456789");
        String one = EmailHasher.hmacHex("user@example.com");
        EmailHasher.configurePepper("pepper-two-0123456789");
        String two = EmailHasher.hmacHex("user@example.com");

        assertThat(one).isNotEqualTo(two).matches("[0-9a-f]{64}");
        assertThat(one).isNotEqualTo(EmailHasher.sha512Hex("user@example.com"));
    }

    @Test
    void hmac_withoutPepper_isNotConfigured_andRefusesToHash() {
        EmailHasher.configurePepper("");

        assertThat(EmailHasher.hmacConfigured()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> EmailHasher.hmacHex("a@b.co"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configurePepper_rejectsTooShortValue() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> EmailHasher.configurePepper("short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
