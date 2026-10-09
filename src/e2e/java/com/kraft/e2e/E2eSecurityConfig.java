package com.kraft.e2e;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * E2E 전용 경로({@code /e2e/**})를 인증 없이 여는 필터 체인. 운영 {@code SecurityConfig}에
 * 이 경로의 permitAll을 두면 운영 보안 설정에 테스트 전용 예외가 남으므로, e2e 소스셋·프로파일에서만
 * 체인을 등록한다. 운영 jar에는 이 클래스가 없다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("e2e")
public class E2eSecurityConfig {

    /** {@code staticResourceChain}(Order 0)과 같은 순위로, 기본 체인(Order 1) 앞. securityMatcher로 좁혀 다른 경로엔 영향이 없다. */
    @Bean
    @Order(0)
    public SecurityFilterChain e2eChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/e2e/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
