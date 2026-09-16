package com.kraft.user.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 이메일 조회·중복확인용 SHA-512 해시. {@code User.email}은 AES로 암호화되어 등호 조회가
 * 불가능하므로, 동일한 이메일이 항상 동일한 해시를 갖는 이 값을 별도 컬럼({@code email_hash})에
 * 저장해 조회·유니크 제약에 사용한다.
 */
public final class EmailHasher {

    private EmailHasher() {
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
