package com.kraft.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kraft.security")
public record SecurityProperties(
        String trustedProxyCidr,
        int rateLimitPerMinute,
        int rateLimitMaxKeys,
        String rateLimitBackend
) {}
