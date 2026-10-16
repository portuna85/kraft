package com.kraft.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.Encryptors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이메일 암호화는 deprecated였던 {@code Encryptors.delux}에서 옮겨 왔다. 이미 저장된 암호문을 계속 읽는 것이 가장 중요하므로 옛 형식을 고정한다.
 */
class EmailEncryptionTest {

    private static final String KEY = "test-key-for-golden-vector-0123456789";
    private static final String SALT = "a1b2c3d4e5f60718";

    /** 옛 {@code Encryptors.delux(KEY, SALT).encrypt("golden@example.com")}이 실제로 만든 암호문. */
    private static final String LEGACY_CIPHERTEXT =
            "a1af777e23d80d5ae3c1726875c53897757ce2c5b08943dbda2f19bdba9b130522fd6221c67a0fafef675e563d47026c2b11";

    @Test
    @DisplayName("옛 delux가 만든 저장된 암호문을 그대로 복호화한다")
    void decryptsCiphertextStoredByLegacyEncryptor() {
        assertThat(EmailEncryption.encryptor(KEY).decrypt(LEGACY_CIPHERTEXT)).isEqualTo("golden@example.com");
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("새 암호기가 만든 암호문을 옛 delux도 복호화한다(롤백해도 읽힌다)")
    void legacyEncryptorCanReadNewCiphertext() {
        String encrypted = EmailEncryption.encryptor(KEY).encrypt("한글+tag@example.com");

        assertThat(Encryptors.delux(KEY, SALT).decrypt(encrypted)).isEqualTo("한글+tag@example.com");
    }

    @Test
    @DisplayName("같은 평문도 호출마다 다른 암호문이 나오고(IV), 모두 원문으로 복호화된다")
    void encryptionIsNonDeterministicButRoundTrips() {
        var encryptor = EmailEncryption.encryptor(KEY);

        String first = encryptor.encrypt("a@example.com");
        String second = encryptor.encrypt("a@example.com");

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first)).isEqualTo("a@example.com");
        assertThat(encryptor.decrypt(second)).isEqualTo("a@example.com");
    }

    @Test
    @DisplayName("다른 키로는 복호화되지 않는다")
    void wrongKeyFails() {
        String encrypted = EmailEncryption.encryptor(KEY).encrypt("a@example.com");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> EmailEncryption.encryptor("another-key-0123456789abcdef").decrypt(encrypted))
                .isInstanceOf(RuntimeException.class);
    }
}
