package com.kraft.domain.user;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * {@code users.email} 암호화기를 만드는 단 하나의 자리.
 * <p>
 * 키 유도 salt가 여기에만 있는 것이 중요하다. 이 값이 {@link EmailAttributeConverter}와
 * 키 교체 도구에 따로 적혀 있으면, 한쪽만 바뀌는 순간 <b>저장된 모든 이메일을 복호화할 수
 * 없게 된다</b> — 게다가 조회는 키를 쓰지 않는 {@code email_hash}로 하므로 로그인은 계속
 * 되어 한참 뒤에야 발견된다.
 */
public final class EmailEncryption {

    // AES 키 유도용 고정 salt. 비밀 값이 아니라 키 유도 입력일 뿐이며, 실제 보안은
    // app.security.email-encryption-key(비밀 키)에서 나온다.
    // 이 값을 바꾸면 기존에 저장된 이메일을 읽을 수 없다.
    private static final String KEY_DERIVATION_SALT = "a1b2c3d4e5f60718";

    private EmailEncryption() {
    }

    /**
     * 매 호출마다 IV가 달라지는 비결정적 암호화({@code Encryptors.delux}, AES-256-GCM 기반).
     * 같은 주소라도 암호문이 매번 달라지므로 등호 비교가 불가능하고, 그래서 조회는
     * {@link EmailHasher}의 결정적 해시로 한다.
     */
    public static TextEncryptor encryptor(String key) {
        return Encryptors.delux(key, KEY_DERIVATION_SALT);
    }
}
