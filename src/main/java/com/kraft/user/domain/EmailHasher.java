package com.kraft.user.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 이메일 조회·중복확인용 SHA-512 해시. {@code User.email}은 AES로 암호화되어 등호 조회가
 * 불가능하므로, 동일한 이메일이 항상 동일한 해시를 갖는 이 값을 별도 컬럼({@code email_hash})에
 * 저장해 조회·유니크 제약에 사용한다.
 */
public final class EmailHasher {

    private EmailHasher() {
    }

    /** pepper의 최소 길이. 짧은 값은 HMAC의 이점(키 없는 해시 대비 사전 대입 저항)을 크게 깎는다. */
    static final int MIN_PEPPER_LENGTH = 16;

    private static volatile byte[] pepper;

    /**
     * 기동 시 한 번 설정한다({@link EmailHmacConfiguration}). {@code User}의 JPA 콜백이 정적으로
     * 부르는 구조라 주입 대신 정적 보관을 쓴다. 비어 있으면 설정하지 않은 것으로 본다 —
     * 이메일 HMAC 없이 도는 최소 슬라이스 테스트와 rekey 도구를 위한 것이고, 운영은
     * {@code EMAIL_HASH_PEPPER}가 필수라 이 경로로 오지 않는다.
     */
    public static void configurePepper(String value) {
        if (value == null || value.isBlank()) {
            pepper = null;
            return;
        }
        if (value.length() < MIN_PEPPER_LENGTH) {
            throw new IllegalStateException("이메일 해시 pepper는 최소 " + MIN_PEPPER_LENGTH + "자여야 합니다.");
        }
        pepper = value.getBytes(StandardCharsets.UTF_8);
    }

    public static boolean hmacConfigured() {
        return pepper != null;
    }

    /**
     * 이메일 조회용 HMAC-SHA256(pepper, email)의 소문자 16진수. 토큰 해시는 122비트 난수라
     * 사전 대입 대상이 아니므로 {@link #sha512Hex}를 그대로 쓴다.
     */
    public static String hmacHex(String email) {
        byte[] key = pepper;
        if (key == null) {
            throw new IllegalStateException("app.security.email-hash-pepper가 설정되지 않았습니다.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(email.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256을 사용할 수 없습니다.", e);
        }
    }

    /**
     * 이 메서드는 로그인마다(회원 조회) 그리고 {@code User}의 모든 insert/update마다
     * ({@code @PrePersist}/{@code @PreUpdate}) 호출되는 뜨거운 경로다. 바이트 64개마다
     * {@code String.format("%02x", ...)}를 반복하던 것은 서식 문자열을 매번 다시 해석하는
     * 비용이 든다(개선 보고서 "해시 문자열 생성의 반복 포맷 비용"). {@link HexFormat}은 같은
     * 소문자·구분자 없는 16진수를 바이트 배열 전체에 대해 한 번에 만든다.
     */
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
