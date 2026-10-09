package com.kraft.user.domain;

/**
 * 로그·오류 메시지에 남길 이메일을 가린다 — 암호화해 저장한 주소가 로그에 평문으로 남으면 보호 범위가 줄어든다. local part는
 * 첫 글자만 남기고 도메인은 둔다(발송 실패 조사에는 도메인이 필요하다).
 * <pre>
 * someone@example.com → s***@example.com
 * a@example.com       → ***@example.com
 * </pre>
 * 희귀한 도메인은 사람을 좁힐 수 있으므로, id를 알 수 있는 자리에서는 id를 남기는 편이 낫다.
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
