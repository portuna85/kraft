package com.kraft.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * M-10: {@code trustedProxyCidr}은 여기서 형식 검증하지 않는다 — {@link
 * com.kraft.common.web.ClientIpResolver}가 기동 시점에 이미 CIDR 문법(네트워크/프리픽스,
 * 프리픽스 범위)을 파싱해 실패하면 즉시 기동을 막는다(fail-fast). 빈 문자열은 "신뢰하는
 * 프록시 없음"이라는 유효한 설정이라 여기서 @NotBlank로 막으면 안 된다. rateLimitBackend는
 * {@code @ConditionalOnProperty}로 빈 선택이 갈리는데, 알 수 없는 값이면 두 구현체 어느
 * 쪽도 등록되지 않아 "RateLimitCounter 타입 빈이 없다"는 불명확한 오류로 이어졌다 —
 * 여기서 허용값을 명시해 더 이르고 명확한 시점에 실패하게 한다.
 */
@Validated
@ConfigurationProperties(prefix = "kraft.security")
public record SecurityProperties(
        String trustedProxyCidr,
        @Min(1) @Max(100_000) int rateLimitPerMinute,
        @Min(1) @Max(1_000_000) int rateLimitMaxKeys,
        @Pattern(regexp = "in-memory|redis") String rateLimitBackend
) {}
