package com.kraft.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    SavedProperties.class,
    OpsProperties.class,
    ExternalLottoProperties.class,
    SecurityProperties.class,
    RevalidateProperties.class,
    CommunityProperties.class,
    PublicBaseUrlProperties.class,
    WebObservabilityProperties.class
})
public class PropertiesConfig {
}
