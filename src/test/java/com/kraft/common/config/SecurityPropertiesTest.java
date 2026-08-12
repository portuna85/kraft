package com.kraft.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M-10: {@link SecurityProperties}에 추가한 {@code @Validated} 제약이 실제로 기동을
 * 막는지 확인한다 — {@link org.springframework.boot.test.context.runner.ApplicationContextRunner}로
 * 전체 앱을 띄우지 않고 프로퍼티 바인딩만 가볍게 검증한다.
 */
@DisplayName("SecurityProperties 검증 테스트")
class SecurityPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(SecurityProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, PropertyPlaceholderAutoConfiguration.class);

    private static final String[] VALID_PROPS = {
            "kraft.security.trusted-proxy-cidr=172.28.0.0/16",
            "kraft.security.rate-limit-per-minute=120",
            "kraft.security.rate-limit-max-keys=10000",
            "kraft.security.rate-limit-backend=in-memory",
    };

    @Test
    @DisplayName("정상 값이면 컨텍스트가 정상적으로 뜬다")
    void validValues_contextStartsSuccessfully() {
        contextRunner.withPropertyValues(VALID_PROPS)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("rate-limit-backend가 허용값이 아니면 기동을 막는다")
    void unknownRateLimitBackend_failsToStart() {
        contextRunner.withPropertyValues(
                        "kraft.security.trusted-proxy-cidr=172.28.0.0/16",
                        "kraft.security.rate-limit-per-minute=120",
                        "kraft.security.rate-limit-max-keys=10000",
                        "kraft.security.rate-limit-backend=not-a-real-backend")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("rate-limit-per-minute이 0 이하면 기동을 막는다")
    void nonPositiveRateLimitPerMinute_failsToStart() {
        contextRunner.withPropertyValues(
                        "kraft.security.trusted-proxy-cidr=172.28.0.0/16",
                        "kraft.security.rate-limit-per-minute=0",
                        "kraft.security.rate-limit-max-keys=10000",
                        "kraft.security.rate-limit-backend=in-memory")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("rate-limit-max-keys가 0 이하면 기동을 막는다")
    void nonPositiveRateLimitMaxKeys_failsToStart() {
        contextRunner.withPropertyValues(
                        "kraft.security.trusted-proxy-cidr=172.28.0.0/16",
                        "kraft.security.rate-limit-per-minute=120",
                        "kraft.security.rate-limit-max-keys=0",
                        "kraft.security.rate-limit-backend=in-memory")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("trusted-proxy-cidr이 빈 문자열이어도(신뢰 프록시 없음) 기동을 막지 않는다")
    void blankTrustedProxyCidr_stillStartsSuccessfully() {
        contextRunner.withPropertyValues(
                        "kraft.security.trusted-proxy-cidr=",
                        "kraft.security.rate-limit-per-minute=120",
                        "kraft.security.rate-limit-max-keys=10000",
                        "kraft.security.rate-limit-backend=in-memory")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
