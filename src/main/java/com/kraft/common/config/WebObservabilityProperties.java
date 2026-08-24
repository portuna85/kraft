package com.kraft.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OBS-WEB-01(docs/improvement.md): web 컨테이너가 vitals/CSP/client-error 이벤트를
 * 내부 네트워크 경계(app/monitoring 격리) 밖에서 보내지 못하므로, revalidate 웹훅과
 * 반대 방향(web → backend)의 secret-guarded 호출로 이 backend에 push한다.
 */
@ConfigurationProperties(prefix = "kraft.web-observability")
public record WebObservabilityProperties(
        String secret
) {
}
