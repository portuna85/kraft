package com.kraft.domain.user;

/**
 * 로그·오류 메시지에 남길 이메일을 가린다.
 *
 * <h3>왜 필요한가</h3>
 * {@code users.email}은 암호화해 저장하지만, 그 주소가 <b>로그 파일에는 평문으로</b> 남으면
 * 암호화가 지켜 주는 범위가 줄어든다. 로그는 DB보다 다루기 쉽고 오래 남으며, 종종 그대로
 * 복사되어 나간다(개선 보고서 "자격 증명 관리" — 로그에 남는 이메일 최소화).
 *
 * <h3>무엇을 남기고 무엇을 가리는가</h3>
 * 사람을 가리키는 쪽(local part)은 첫 글자만 남기고, 도메인은 그대로 둔다.
 * <pre>
 * someone@example.com → s***@example.com
 * ab@example.com      → a***@example.com
 * a@example.com       → ***@example.com
 * </pre>
 * 도메인을 남기는 것은 의도한 절충이다. 발송 실패를 조사할 때 실제로 쓰이는 정보가 도메인
 * 쪽이기 때문이다("특정 메일 서버로만 실패하는가"). 다만 회원이 적고 도메인이 희귀하면
 * 도메인만으로도 사람이 좁혀질 수 있으므로, <b>가릴 수 있으면 id를 남기는 편이 낫다</b> —
 * 이 유틸리티는 id를 알 수 없는 자리에서만 쓴다.
 */
public final class EmailMasker {

    private static final String HIDDEN = "***";

    private EmailMasker() {
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return HIDDEN;
        }

        int at = email.lastIndexOf('@');
        // 형식이 이상한 값은 통째로 가린다. 무엇이 들어 있는지 모르는 문자열을 남기지 않는다.
        if (at <= 0) {
            return HIDDEN;
        }

        String local = email.substring(0, at);
        String domain = email.substring(at);
        // 한 글자짜리 local part는 첫 글자를 남기는 것이 곧 전부를 남기는 것이다.
        return local.length() <= 1 ? HIDDEN + domain : local.charAt(0) + HIDDEN + domain;
    }
}
