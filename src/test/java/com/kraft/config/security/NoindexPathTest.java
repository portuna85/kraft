package com.kraft.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** X-Robots-Tag를 붙일 경로 판정(평가 보고서 2026-09-25 F08). 접두어만 같은 다른 경로는 막지 않는다. */
class NoindexPathTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "/login, true",
            "/signup, true",
            "/forgot-password, true",
            "/users/password-reset, true",
            "/users/verify, true",
            "/admin, true",
            "/admin/reports, true",
            "/posts/save, true",
            "/api/v1/posts, true",
            "/, false",
            "/recommend, false",
            "/posts/update/1, false",
            "/loginx, false",
            "/administrator, false",
            "/posts/saved, false",
            "/robots.txt, false",
    })
    @DisplayName("색인 제외 경로 판정")
    void isNoindexPath(String path, boolean expected) {
        assertThat(SecurityConfig.isNoindexPath(new MockHttpServletRequest("GET", path))).isEqualTo(expected);
    }
}
