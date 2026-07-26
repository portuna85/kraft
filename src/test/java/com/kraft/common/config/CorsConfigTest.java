package com.kraft.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * B-07: CorsConfig는 build.gradle.kts의 광역 제외 패턴 "**\/config/**"에 걸려 커버리지
 * 집계에서는 빠지지만, resolvedOrigins()는 prod에서 CORS 허용 출처를 결정하는 실제
 * 보안 로직이라 단위 테스트로 별도 커버한다.
 */
@DisplayName("CORS 허용 출처 결정 로직 테스트")
class CorsConfigTest {

    @Test
    @DisplayName("public-base-url이 설정되어 있으면 그 값 하나만 허용 출처로 반환한다")
    void resolvedOrigins_returnsConfiguredUrl_whenSet() {
        CorsConfig config = new CorsConfig(new PublicBaseUrlProperties("https://kraft.example"));

        assertThat(config.resolvedOrigins()).containsExactly("https://kraft.example");
    }

    @Test
    @DisplayName("public-base-url이 비어 있으면 로컬 개발용 와일드카드로 폴백한다")
    void resolvedOrigins_fallsBackToWildcard_whenBlank() {
        CorsConfig config = new CorsConfig(new PublicBaseUrlProperties(""));

        assertThat(config.resolvedOrigins()).containsExactly("*");
    }

    @Test
    @DisplayName("public-base-url이 null이면 로컬 개발용 와일드카드로 폴백한다")
    void resolvedOrigins_fallsBackToWildcard_whenNull() {
        CorsConfig config = new CorsConfig(new PublicBaseUrlProperties(null));

        assertThat(config.resolvedOrigins()).containsExactly("*");
    }
}
