package com.kraft.common.config;

import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    private final PublicBaseUrlProperties publicBaseUrlProperties;

    public CorsConfig(PublicBaseUrlProperties publicBaseUrlProperties) {
        this.publicBaseUrlProperties = publicBaseUrlProperties;
    }

    // 허용 출처 목록: kraft.public-base-url이 설정된 경우 그 값만 허용.
    // 미설정 시 "*" 폴백(로컬 개발용). prod 프로파일에서는 ProdEnvironmentValidator가
    // kraft.public-base-url 필수 설정을 강제하므로 프로덕션에서 "*"가 사용되지 않는다.
    List<String> resolvedOrigins() {
        String publicBaseUrl = publicBaseUrlProperties.publicBaseUrl();
        return publicBaseUrl != null && !publicBaseUrl.isBlank()
                ? List.of(publicBaseUrl)
                : List.of("*");
    }

    // CorsFilter는 RequestIdFilter(HIGHEST) 다음, SecurityHeaders(2)·RateLimit(10)보다 앞에서
    // 실행되어야 429 등 단락 응답에도 CORS 헤더가 붙는다. 순수 @Bean CorsFilter는 순서 미지정
    // (LOWEST_PRECEDENCE)이므로 FilterRegistrationBean으로 순서를 고정한다.
    @Bean
    FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(resolvedOrigins());
        // B-3: DELETE 추가, X-Device-Token 추가
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Content-Type", "Accept", "Authorization",
                "X-Device-Token", "X-Request-Id"
        ));
        // S-2: X-RateLimit-Reset 미발급이므로 제거, X-RateLimit-Limit 추가
        config.setExposedHeaders(List.of("X-Request-Id", "X-RateLimit-Limit", "X-RateLimit-Remaining"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(1);
        return registration;
    }
}
