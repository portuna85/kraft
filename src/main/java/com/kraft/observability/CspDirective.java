package com.kraft.observability;

import java.util.Locale;

/**
 * OBS-WEB-01(docs/improvement.md): 브라우저가 보내는 CSP violation report의
 * {@code violated-directive}는 이 앱의 정책(web/src/shared/config/csp.ts)이 실제로
 * 선언한 directive 이름으로 사실상 바운드돼 있지만, 방어적으로 알려진 목록 밖은
 * {@link #OTHER}로 묶는다 — {@link RouteBucket}과 같은 이유다.
 */
enum CspDirective {
    DEFAULT_SRC("default-src"),
    SCRIPT_SRC("script-src"),
    STYLE_SRC("style-src"),
    IMG_SRC("img-src"),
    FONT_SRC("font-src"),
    CONNECT_SRC("connect-src"),
    FRAME_SRC("frame-src"),
    OBJECT_SRC("object-src"),
    BASE_URI("base-uri"),
    FORM_ACTION("form-action"),
    FRAME_ANCESTORS("frame-ancestors"),
    OTHER("other");

    private final String tagValue;

    CspDirective(String tagValue) {
        this.tagValue = tagValue;
    }

    /**
     * 브라우저는 {@code violated-directive}를 {@code "script-src 'self'"}처럼 값까지
     * 붙여 보낼 수 있다 — 첫 토큰(directive 이름)만 비교한다.
     */
    static CspDirective of(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        String name = raw.trim().toLowerCase(Locale.ROOT).split(" ", 2)[0];
        for (CspDirective directive : values()) {
            if (directive.tagValue.equals(name)) {
                return directive;
            }
        }
        return OTHER;
    }

    String tagValue() {
        return tagValue;
    }
}
