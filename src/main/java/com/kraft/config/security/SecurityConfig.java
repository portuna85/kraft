package com.kraft.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/css/**", "/js/**", "/images/**", "/h2-console/**").permitAll()
                        .requestMatchers("/api/v1/users").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(refererLogoutSuccessHandler())
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/h2-console/**")
                );

        return http.build();
    }

    /**
     * 로그아웃 성공 시 로그아웃을 요청한 그 페이지로 되돌아간다. Referer가 같은 오리진일 때만
     * 신뢰하고, 없거나 외부 도메인이면 "/"로 안전하게 대체한다(오픈 리다이렉트 방지). 단,
     * 게시글 등록 화면(`/posts/save`)에서 로그아웃하는 경우는 예외로 항상 "/"로 보낸다 —
     * 로그아웃하면 익명 사용자가 되어 "글 등록" 자체가 더는 의미가 없는 화면이기 때문이다.
     */
    private LogoutSuccessHandler refererLogoutSuccessHandler() {
        return (request, response, authentication) -> {
            String referer = request.getHeader("Referer");
            String baseUrl = request.getScheme() + "://" + request.getServerName()
                    + (request.getServerPort() == 80 || request.getServerPort() == 443
                            ? "" : ":" + request.getServerPort());
            String redirectUrl = "/";
            if (referer != null && referer.startsWith(baseUrl)) {
                String path = referer.substring(baseUrl.length());
                if (!path.equals("/posts/save") && !path.startsWith("/posts/save?")) {
                    redirectUrl = referer;
                }
            }
            response.sendRedirect(redirectUrl);
        };
    }
}
