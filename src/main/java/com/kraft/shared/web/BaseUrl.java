package com.kraft.shared.web;

/**
 * 설정값 {@code app.base-url}의 끝 {@code /}를 정리하는 곳. 사이트맵·SEO 메타·메일 링크가 모두
 * {@code baseUrl + "/경로"}로 주소를 이어 붙이므로, 설정이 {@code https://kraft.io.kr/}처럼
 * 슬래시로 끝나도 {@code //}가 생기지 않게 한 곳에서 같은 규칙으로 정리한다.
 */
public final class BaseUrl {

    private BaseUrl() {
    }

    /** 끝의 {@code /} 하나를 떼어 낸다. 그 밖의 값은 그대로 돌려준다. */
    public static String normalize(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
