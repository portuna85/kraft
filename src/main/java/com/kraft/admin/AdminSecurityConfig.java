package com.kraft.admin;

import com.kraft.common.web.ClientIpResolver;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class AdminSecurityConfig {

    private final AdminUserDetailsService userDetailsService;
    private final AdminLoginHandler loginHandler;
    private final AdminLoginAttemptService lockout;
    private final ClientIpResolver ipResolver;
    private final MeterRegistry meterRegistry;

    public AdminSecurityConfig(AdminUserDetailsService userDetailsService,
                               AdminLoginHandler loginHandler,
                               AdminLoginAttemptService lockout,
                               ClientIpResolver ipResolver,
                               MeterRegistry meterRegistry) {
        this.userDetailsService = userDetailsService;
        this.loginHandler = loginHandler;
        this.lockout = lockout;
        this.ipResolver = ipResolver;
        this.meterRegistry = meterRegistry;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // maximumSessions(1)이 세션 파기(브라우저 종료·만료 등) 이벤트를 인메모리 SessionRegistry에
    // 반영하려면 이 리스너가 필요하다. 없으면 파기된 세션이 레지스트리에 남아 재로그인이
    // "기존 세션 1개 초과"로 거부되는 문제가 생긴다.
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    AuthenticationManager adminAuthManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        return http
                .securityMatcher("/admin/**")
                .addFilterBefore(new AdminLockoutFilter(lockout, ipResolver, meterRegistry),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(adminLoginCsrfRedirectFilter(csrfTokenRepository), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .successHandler(loginHandler)
                        .failureHandler(loginHandler))
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler(adminAccessDeniedHandler()))
                // I-10: caddy/Caddyfile의 {$KRAFT_ADMIN_DOMAIN} 블록도 사이트 전역으로 이
                // 헤더들을 이미 발급한다. CSP만 Caddy가 못 내는 값(admin 전용 정책)이라 여기서
                // 계속 관리하고, 나머지는 Spring Security 기본값을 꺼서 Caddy를 단일 소스로 둔다.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                                        + "form-action 'self'; frame-ancestors 'none'; object-src 'none'"))
                        .frameOptions(fo -> fo.disable())
                        .contentTypeOptions(cto -> cto.disable())
                        .httpStrictTransportSecurity(hsts -> hsts.disable()))
                .sessionManagement(sm -> sm.maximumSessions(1))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .build();
    }

    private static OncePerRequestFilter adminLoginCsrfRedirectFilter(HttpSessionCsrfTokenRepository csrfTokenRepository) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                if ("POST".equalsIgnoreCase(request.getMethod())
                        && isAdminLoginPath(request)
                        && !hasValidCsrfToken(request, csrfTokenRepository)) {
                    response.sendRedirect("/admin/login?expired");
                    return;
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    private static boolean isAdminLoginPath(HttpServletRequest request) {
        return "/admin/login".equals(request.getServletPath())
                || "/admin/login".equals(request.getRequestURI());
    }

    private static boolean hasValidCsrfToken(HttpServletRequest request,
                                             HttpSessionCsrfTokenRepository csrfTokenRepository) {
        CsrfToken expected = csrfTokenRepository.loadToken(request);
        if (expected == null) {
            return false;
        }

        String actual = request.getParameter(expected.getParameterName());
        if (actual == null) {
            actual = request.getHeader(expected.getHeaderName());
        }
        return expected.getToken().equals(actual);
    }

    private static AccessDeniedHandler adminAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            if (accessDeniedException instanceof CsrfException
                    && isAdminLoginPath(request)) {
                response.sendRedirect("/admin/login?expired");
                return;
            }
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        };
    }
}
