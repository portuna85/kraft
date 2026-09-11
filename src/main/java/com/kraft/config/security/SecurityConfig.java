package com.kraft.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
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
                        .successHandler(redirectAwareSuccessHandler())
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
     * 로그인 폼(`/login`)이 어느 화면에서 왔는지는 네비게이션의 "로그인" 링크가 현재 경로를
     * {@code ?redirect=}로 실어 보내고, 로그인 폼의 히든 필드가 POST까지 그대로 옮겨온다.
     * 이 앱은 페이지 전체를 항상 {@code permitAll}로 열어두므로(보호된 화면에 접근했다가
     * 강제로 로그인 페이지로 튕겨나가는 경로가 없음) Spring Security 기본 {@code RequestCache}가
     * 채워질 일이 없다 — 그래서 그 메커니즘 대신 이 파라미터 하나로 직접 "원래 위치"를 구현한다.
     * 값이 없거나, "/"로 시작하지 않거나(외부 오픈 리다이렉트 방지), 로그인 화면 자기 자신을
     * 가리키면 기본값 "/"로 이동한다.
     */
    private AuthenticationSuccessHandler redirectAwareSuccessHandler() {
        return (request, response, authentication) -> {
            String redirect = request.getParameter("redirect");
            String target = "/";
            if (redirect != null && redirect.startsWith("/") && !redirect.startsWith("//")
                    && !redirect.startsWith("/login")) {
                target = redirect;
            }
            response.sendRedirect(target);
        };
    }

    /**
     * 로그아웃 성공 시 로그아웃을 요청한 그 페이지로 되돌아간다. Referer가 같은 오리진일 때만
     * 신뢰하고, 없거나 외부 도메인이면 "/"로 안전하게 대체한다(오픈 리다이렉트 방지). 다음 두
     * 화면은 예외로 둔다:
     * <ul>
     * <li>게시글 등록 화면(`/posts/save`) — 로그아웃하면 익명 사용자가 되어 "글 등록" 자체가
     * 더는 의미가 없는 화면이므로 "/"로 보낸다.</li>
     * <li>비밀번호 변경 화면(`/users/me/password`) — 변경 성공 후 이 화면으로 되돌아가면
     * "다시 로그인해 주세요" 안내가 로그인 폼 없이 떠 있는 상태가 된다. 항상 "/login"으로
     * 명시적으로 보낸다.</li>
     * </ul>
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
                if (path.equals("/users/me/password") || path.startsWith("/users/me/password?")) {
                    redirectUrl = "/login";
                } else if (!path.equals("/posts/save") && !path.startsWith("/posts/save?")) {
                    redirectUrl = referer;
                }
            }
            response.sendRedirect(redirectUrl);
        };
    }
}
