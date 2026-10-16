package com.kraft.user.domain;

import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;

/**
 * {@code users.email} 암호화기를 만드는 단 하나의 자리 — 키 유도 salt가 {@link EmailAttributeConverter}와 키 교체 도구에 따로
 * 있으면 한쪽만 바뀌는 순간 저장된 모든 이메일을 복호화할 수 없다(조회는 해시로 하므로 로그인은 되어 한참 뒤에야 발견된다).
 * <p>
 * 이전에는 Spring Security의 {@code Encryptors.delux}를 썼다. 그 메서드가 deprecated(대체: {@link AesGcmBytesEncryptor})되었지만 대체 API의
 * 기본 키 유도(PBKDF2WithHmacSHA256, 600,000회)는 {@code delux}의 것(PBKDF2WithHmacSHA1, 1,024회)과 달라 그대로 바꾸면 저장된 모든 이메일을
 * 복호화할 수 없다. 그래서 키 유도는 기존 방식 그대로 직접 하고({@link #deriveKey}), 암호화 자체는 같은 형식(AES-256-GCM, 16바이트 IV를 앞에 붙인 뒤
 * 16진수 인코딩)을 만드는 비-deprecated 클래스를 쓴다. {@code EmailEncryptionTest}가 옛 {@code delux}로 만든 암호문의 복호화를 고정한다.
 */
public final class EmailEncryption {

    // AES 키 유도용 고정 salt. 비밀 값이 아니라 키 유도 입력일 뿐이며, 실제 보안은
    // app.security.email-encryption-key(비밀 키)에서 나온다.
    // 이 값을 바꾸면 기존에 저장된 이메일을 읽을 수 없다.
    private static final String KEY_DERIVATION_SALT = "a1b2c3d4e5f60718";

    // 저장된 암호문과 호환되도록 옛 Encryptors.delux의 키 유도 값을 그대로 둔다. 바꾸면 기존 이메일을 읽을 수 없다.
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final int KEY_DERIVATION_ITERATIONS = 1024;
    private static final int KEY_BITS = 256;

    private EmailEncryption() {
    }

    /** 호출마다 IV가 달라지는 비결정적 암호화(AES-256-GCM)라 암호문으로 등호 비교가 안 되므로, 조회는 {@link EmailHasher}의 결정적 해시로 한다. */
    public static TextEncryptor encryptor(String key) {
        BytesEncryptor bytes = AesGcmBytesEncryptor.withSecretKey(deriveKey(key)).build();
        return new HexTextEncryptor(bytes);
    }

    private static SecretKey deriveKey(String password) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Hex.decode(KEY_DERIVATION_SALT),
                    KEY_DERIVATION_ITERATIONS, KEY_BITS);
            return SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM).generateSecret(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이메일 암호화 키를 유도할 수 없습니다.", e);
        }
    }

    /** 암호문을 16진수 문자열로 저장하는 {@link TextEncryptor}(옛 {@code delux}와 같은 표기). */
    private record HexTextEncryptor(BytesEncryptor encryptor) implements TextEncryptor {

        @Override
        public String encrypt(String text) {
            return new String(Hex.encode(encryptor.encrypt(Utf8.encode(text))));
        }

        @Override
        public String decrypt(String encryptedText) {
            return Utf8.decode(encryptor.decrypt(Hex.decode(encryptedText)));
        }
    }
}
