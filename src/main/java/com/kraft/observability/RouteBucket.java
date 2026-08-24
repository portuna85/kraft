package com.kraft.observability;

import java.util.Locale;

/**
 * OBS-WEB-01(docs/improvement.md): BE-SEC-01이 고친 것과 같은 종류의 버그(임의
 * 문자열이 메트릭 태그가 되어 시계열이 무제한 증가)를 새 코드에 재도입하지 않기 위한
 * 고정 라우트 집합. 프론트가 보내는 {@code route}(브라우저 {@code pathname}, 예:
 * {@code /community/posts/1150})는 자유 문자열이라 그대로 Micrometer 태그로 쓸 수
 * 없다 — 알려진 라우트 집합으로 버킷화하고, 모르는 값은 {@link #OTHER}로 묶는다.
 * {@code PublicRateLimitFilter}의 {@code enum Surface} + {@code surfaceOf(...)}와
 * 같은 패턴이다.
 */
enum RouteBucket {
    HOME,
    RECOMMEND,
    RECOMMEND_HISTORY,
    COMMUNITY_WRITE,
    COMMUNITY_POST,
    COMMUNITY,
    COMPANION,
    FREQUENCY,
    INFO,
    OPS,
    SAVED,
    STATS,
    STATUS,
    DATA,
    ANALYSIS,
    OTHER;

    static RouteBucket of(String path) {
        if (path == null) {
            return OTHER;
        }
        if (path.equals("/")) {
            return HOME;
        }
        if (path.startsWith("/recommend/history")) {
            return RECOMMEND_HISTORY;
        }
        if (path.startsWith("/recommend")) {
            return RECOMMEND;
        }
        if (path.startsWith("/community/write")) {
            return COMMUNITY_WRITE;
        }
        if (path.startsWith("/community/posts/")) {
            return COMMUNITY_POST;
        }
        if (path.startsWith("/community")) {
            return COMMUNITY;
        }
        if (path.startsWith("/companion")) {
            return COMPANION;
        }
        if (path.startsWith("/frequency")) {
            return FREQUENCY;
        }
        if (path.startsWith("/info")) {
            return INFO;
        }
        if (path.startsWith("/ops")) {
            return OPS;
        }
        if (path.startsWith("/saved")) {
            return SAVED;
        }
        if (path.startsWith("/stats")) {
            return STATS;
        }
        if (path.startsWith("/status")) {
            return STATUS;
        }
        if (path.startsWith("/data")) {
            return DATA;
        }
        if (path.startsWith("/analysis")) {
            return ANALYSIS;
        }
        return OTHER;
    }

    String tagValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
