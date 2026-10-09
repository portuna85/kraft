package com.kraft.user.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 이메일 조회·중복확인용 해시. {@code User.email}은 AES로 암호화돼 등호 조회가 안 되므로, 같은 이메일이 항상 같은 값을
 * 갖는 별도 컬럼으로 조회·유니크 제약을 건다.
 */
public final class EmailHasher {

    private EmailHasher() {
    }

    /** pepper의 최소 길이. 짧은 값은 HMAC의 이점(키 없는 해시 대비 사전 대입 저항)을 크게 깎는다. */
    static final int MIN_PEPPER_LENGTH = 16;

    private static volatile byte[] pepper;
    /** 키를 넣어 둔 Mac. 호출마다 init하는 대신 복제해 쓴다(Mac은 스레드 안전하지 않다). */
    private static volatile Mac macPrototype;

    /**
     * 기동 시 한 번 설정한다({@link EmailHmacConfiguration}). {@code User}의 JPA 콜백이 정적으로 부르는 구조라 주입 대신
     * 정적 보관이다. 비어 있으면 미설정으로 본다(HMAC 없이 도는 최소 슬라이스 테스트와 rekey 도구용 — 운영은
     * {@code EMAIL_HASH_PEPPER}가 필수).
     */
    public static void configurePepper(String value) {
        if (value == null || value.isBlank()) {
            pepper = null;
            macPrototype = null;
            return;
        }
        if (value.length() < MIN_PEPPER_LENGTH) {
            throw new IllegalStateException("이메일 해시 pepper는 최소 " + MIN_PEPPER_LENGTH + "자여야 합니다.");
        }
        byte[] key = value.getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            macPrototype = mac;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256을 사용할 수 없습니다.", e);
        }
        pepper = key;
    }

    public static boolean hmacConfigured() {
        return pepper != null;
    }

    /** 이메일 조회용 HMAC-SHA256(pepper, email)의 소문자 16진수. 토큰 해시는 122비트 난수라 사전 대입 대상이 아니므로 {@link #sha512Hex}를 쓴다. */
    public static String hmacHex(String email) {
        Mac prototype = macPrototype;
        if (prototype == null) {
            throw new IllegalStateException("app.security.email-hash-pepper가 설정되지 않았습니다.");
        }
        try {
            Mac mac = (Mac) prototype.clone();
            return HexFormat.of().formatHex(mac.doFinal(email.getBytes(StandardCharsets.UTF_8)));
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("HMAC-SHA256을 복제할 수 없습니다.", e);
        }
    }

    /** SHA-512 소문자 16진수. 로그인마다, {@code User}의 모든 insert/update마다 도는 뜨거운 경로라 {@code String.format} 반복 대신 {@link HexFormat}으로 한 번에 만든다. */
    public static String sha512Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
