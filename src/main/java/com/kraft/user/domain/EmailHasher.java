package com.kraft.user.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 이메일 조회·중복확인용 SHA-512 해시. {@code User.email}은 AES로 암호화되어 등호 조회가
 * 불가능하므로, 동일한 이메일이 항상 동일한 해시를 갖는 이 값을 별도 컬럼({@code email_hash})에
 * 저장해 조회·유니크 제약에 사용한다.
 */
public final class EmailHasher {

    private EmailHasher() {
    }

    public static String sha512Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
