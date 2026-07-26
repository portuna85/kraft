package com.kraft.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * B-08: CorsConfig·CommunitySecurityConfig가 각자 {@code @Value("${kraft.public-base-url:}")}로
 * 같은 값을 원시 주입받고 있었다 — 다른 설정과 일관되게 타입드 record로 옮긴다.
 */
@ConfigurationProperties(prefix = "kraft")
public record PublicBaseUrlProperties(
        String publicBaseUrl
) {
}
