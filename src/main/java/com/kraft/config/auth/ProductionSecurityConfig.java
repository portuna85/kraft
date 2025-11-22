package com.kraft.config.auth;

import com.kraft.domain.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 프로덕션 환경 보안 설정
 * CSRF 활성화 및 강화된 보안 정책 적용
 */
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Configuration
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "prod")
public class ProductionSecurityConfig {

    private static final String ROOT_PATH = "/";
    private static final String[] STATIC_RESOURCES = {"/css/**", "/images/**", "/js/**"};
    private static final String[] PUBLIC_ENDPOINTS = {"/profile"};
    private static final String[] AUTH_ENDPOINTS = {"/api/users/signup", "/api/users/login", "/api/users/logout"};
    private static final String API_PATTERN = "/api/**";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF 활성화 (프로덕션)
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/h2-console/**") // H2는 개발 환경에서만 사용
                )
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .xssProtection(xss -> xss.disable())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROOT_PATH).permitAll()
                        .requestMatchers(STATIC_RESOURCES).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(API_PATTERN).hasAuthority(Role.USER.getAuthority())
                        .anyRequest().authenticated()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl(ROOT_PATH)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                )
                .build();
    }
}

