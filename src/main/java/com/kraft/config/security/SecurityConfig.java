package com.kraft.config.security;

import com.kraft.shared.web.SafeRedirect;
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
                        .requestMatchers("/", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/api/v1/users").permitAll()
                        // 비밀번호를 잊은 사람은 로그인할 수 없다. 이 두 경로만 열어 두고,
                        // 실제 경계는 메일로 보낸 1회용 토큰이 잡는다(PasswordResetService).
                        .requestMatchers("/api/v1/users/password-reset", "/api/v1/users/password-reset/confirm")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
                        // 신고 처리는 관리자만 한다. 화면(/admin/**)과 API(/api/v1/admin/**)를
                        // 같은 규칙으로 막아, 화면을 감추는 것으로 끝내지 않는다.
                        .requestMatchers("/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
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
                );

        return http.build();
    }

    /**
     * 로그인 폼(`/login`)이 어느 화면에서 왔는지는 네비게이션의 "로그인" 링크가 현재 경로를
     * {@code ?redirect=}로 실어 보내고, 로그인 폼의 히든 필드가 POST까지 그대로 옮겨온다.
     * 이 앱은 페이지 전체를 항상 {@code permitAll}로 열어두므로(보호된 화면에 접근했다가
     * 강제로 로그인 페이지로 튕겨나가는 경로가 없음) Spring Security 기본 {@code RequestCache}가
     * 채워질 일이 없다 — 그래서 그 메커니즘 대신 이 파라미터 하나로 직접 "원래 위치"를 구현한다.
     * 값이 앱 내부 경로로 확정되지 않거나({@link SafeRedirect#internalPath}) 로그인 화면 자기
     * 자신을 가리키면 기본값 "/"로 이동한다.
     */
    private AuthenticationSuccessHandler redirectAwareSuccessHandler() {
        return (request, response, authentication) -> {
            String target = SafeRedirect.internalPath(request.getParameter("redirect"), "/");
            if (target.startsWith("/login")) {
                target = "/";
            }
            response.sendRedirect(target);
        };
    }

    /**
     * 로그아웃 성공 시 로그아웃을 요청한 그 페이지로 되돌아간다. Referer가 같은 오리진일 때만
     * 신뢰하고, 없거나 외부 도메인이면 "/"로 안전하게 대체한다(오픈 리다이렉트 방지).
     * <p>
     * 두 가지 예외가 있다:
     * <ul>
     * <li>{@code next} 파라미터가 있으면 그곳으로 보낸다 — 로그아웃 폼(layout/footer.html)이
     * 목적지를 지정할 수 있게 하는 값이다. 로그인 성공 후 복귀와 똑같이
     * {@link SafeRedirect#internalPath}로 앱 내부 경로임을 확인한 뒤에만 신뢰한다.</li>
     * <li>게시글 등록 화면(`/posts/save`) — 로그아웃하면 익명 사용자가 되어 "글 등록" 자체가
     * 더는 의미가 없는 화면이므로 "/"로 보낸다.</li>
     * </ul>
     */
    private LogoutSuccessHandler refererLogoutSuccessHandler() {
        return (request, response, authentication) -> {
            String next = request.getParameter("next");
            if (next != null && !next.isBlank()) {
                response.sendRedirect(SafeRedirect.internalPath(next, "/"));
                return;
            }

            String path = SafeRedirect.sameOriginPathOf(request.getHeader("Referer"), request);
            String redirectUrl = "/";
            if (path != null && !path.equals("/posts/save") && !path.startsWith("/posts/save?")) {
                redirectUrl = path;
            }
            response.sendRedirect(redirectUrl);
        };
    }
}
