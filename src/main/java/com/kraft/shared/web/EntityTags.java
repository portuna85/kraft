package com.kraft.shared.web;

import com.kraft.shared.exception.PreconditionRequiredException;

/**
 * 엔티티 버전(낙관적 잠금)을 HTTP ETag·If-Match로 옮기는 규칙.
 * <p>
 * ETag는 강한 형태({@code "3"})로 낸다. If-Match는 강한 비교를 쓰므로(RFC 9110 §13.1.1) 약한 ETag({@code W/"3"})는
 * 어떤 값과도 일치할 수 없다. 다만 들어오는 값은 {@code W/} 접두사를 관대하게 받는다 — 압축·프록시가 강한
 * ETag를 약한 것으로 바꿀 수 있어서다.
 */
public final class EntityTags {

    /**
     * 어떤 버전과도 일치하지 않는 값. 형식이 틀리거나 여러 값을 나열한 {@code If-Match}를 "일치 없음"으로 다뤄
     * 서비스의 버전 검사가 그대로 412를 내게 한다(버전은 0 이상이다).
     */
    static final long NO_MATCH = -1L;

    private EntityTags() {
    }

    /** 응답 ETag 헤더에 실을 값. */
    public static String of(Long version) {
        return "\"" + version + "\"";
    }

    /**
     * 수정 요청의 기준 버전을 {@code If-Match} 헤더에서 읽는다. 헤더가 없으면 428이다 — 기준 없이 받으면 오래된
     * 화면이 다른 사람의 저장을 말없이 덮어쓴다. 배포 직전까지 열려 있던 옛 화면은 헤더 없이 본문
     * {@code version}만 보내는데, 그 요청도 428로 거절되고 화면에는 새로고침 안내가 뜬다(본문 {@code version}은
     * 더 이상 읽지 않는다).
     *
     * @return 기준 버전. {@code If-Match: *}는 "존재하기만 하면 된다"는 뜻이라 {@code null}(검사 생략)이다.
     * @throws PreconditionRequiredException 헤더가 없거나 비어 있을 때
     */
    public static Long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("수정할 대상의 버전 정보가 필요합니다. 화면을 새로고침한 뒤 다시 시도하세요.");
        }
        return parse(ifMatch);
    }

    /** {@code "3"}, {@code W/"3"}를 3으로 읽는다. {@code *}는 null, 그 밖의 형식은 {@link #NO_MATCH}다. */
    static Long parse(String ifMatch) {
        String value = ifMatch.trim();
        if (value.equals("*")) {
            return null;
        }
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            return NO_MATCH;
        }
        String digits = value.substring(1, value.length() - 1);
        if (!digits.chars().allMatch(Character::isDigit)) {
            // 쉼표로 나열한 여러 값("1", "2")도 여기서 걸린다 — 하나의 기준 버전을 정할 수 없다.
            return NO_MATCH;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException tooLarge) {
            return NO_MATCH;
        }
    }
}
