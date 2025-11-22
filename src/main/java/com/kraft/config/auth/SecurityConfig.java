package com.kraft.config.auth;

import com.kraft.common.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Configuration
@Profile({"local", "dev", "test"})
public class SecurityConfig {

    private static final String ROOT_PATH = "/";
    private static final String[] STATIC_RESOURCES = {"/css/**", "/images/**", "/js/**"};
    private static final String[] PUBLIC_ENDPOINTS = {"/profile", "/h2-console/**"};
    private static final String[] AUTH_ENDPOINTS = {"/api/users/signup", "/api/users/login", "/api/users/logout"};
    private static final String API_PATTERN = "/api/**";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROOT_PATH).permitAll()
                        .requestMatchers(STATIC_RESOURCES).permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(API_PATTERN).authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .build();
    }
}

