package com.kraft.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseUrlTest {

    @Test
    @DisplayName("끝의 슬래시 하나를 떼어 낸다")
    void normalize_stripsTrailingSlash() {
        assertThat(BaseUrl.normalize("https://kraft.io.kr/")).isEqualTo("https://kraft.io.kr");
    }

    @Test
    @DisplayName("슬래시가 없으면 그대로 둔다")
    void normalize_keepsValueWithoutTrailingSlash() {
        assertThat(BaseUrl.normalize("https://kraft.io.kr")).isEqualTo("https://kraft.io.kr");
    }
}
