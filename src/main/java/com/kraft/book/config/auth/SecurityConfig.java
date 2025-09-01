// src/main/java/com/kraft/book/config/auth/SecurityConfig.java
package com.kraft.book.config.auth;

import com.kraft.book.config.CustomOAuth2UserService;
import com.kraft.book.domain.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // H2 콘솔/REST 편의
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/h2-console/**"))
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/css/**", "/images/**", "/js/**",
                                "/h2-console/**", "/profile", "/error",
                                "/oauth2/**", "/login/oauth2/**"  // 로그인 흐름 경로 열어줌
                        ).permitAll()
                        .requestMatchers("/api/v1/**").hasRole("USER")
                        .anyRequest().authenticated()
                )
                .logout(l -> l.logoutSuccessUrl("/"))

                .oauth2Login(oauth -> oauth
                        // optional: 커스텀 로그인 페이지를 루트로 쓸 때
                        //.loginPage("/")
                        .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                        // 실패/성공을 명시적으로 처리해 999 방지
                        .successHandler((req, res, auth) -> res.sendRedirect("/"))
                        .failureHandler((req, res, ex) -> {
                            req.getSession().setAttribute("oauth2_error", ex.getMessage());
                            res.sendRedirect("/login?error");
                        })
                );

        return http.build();
    }
}