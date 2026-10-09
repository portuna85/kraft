package com.kraft.config.security;

import com.kraft.shared.web.SafeRedirect;
import com.kraft.user.service.SessionRevoker;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** 이 빈이 있어야 인증 성공·실패 이벤트가 발행된다({@link LoginLockoutService}가 듣는다). */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
    }

    /** 인증 관련 요청 제한. 빈으로 두어야 그 안의 {@code @Scheduled} 집계 보고가 돈다. */
    @Bean
    public AuthRateLimitFilter authRateLimitFilter(
            @Value("${app.auth.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.auth.rate-limit.login-per-minute:20}") int loginPerMinute,
            @Value("${app.auth.rate-limit.login-account-per-minute:10}") int loginAccountPerMinute,
            @Value("${app.auth.rate-limit.signup-per-minute:5}") int signupPerMinute,
            @Value("${app.auth.rate-limit.password-reset-per-minute:5}") int passwordResetPerMinute,
            @Value("${app.auth.rate-limit.resend-per-minute:5}") int resendPerMinute,
            ObjectMapper objectMapper) {
        return new AuthRateLimitFilter(enabled, loginPerMinute, loginAccountPerMinute, signupPerMinute,
                passwordResetPerMinute, resendPerMinute, objectMapper);
    }

    /** 필터 빈의 서블릿 컨테이너 자동 등록을 끈다 — 보안 체인에만 정확한 위치로 등록한다. */
    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(
            AuthRateLimitFilter authRateLimitFilter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(authRateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 정적 자원 전용 체인({@code @Order(0)}). 기본 {@code Cache-Control: no-store}가 정적 자원의 캐시 헤더를
     * 덮어쓰지 않게 캐시 헤더 라이터만 끈다. {@code web.ignoring()}은 다른 보안 헤더까지 없애므로 쓰지 않는다.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain staticResourceChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/css/**", "/js/**", "/images/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.cacheControl(HeadersConfigurer.CacheControlConfig::disable));

        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http, AuthRateLimitFilter authRateLimitFilter)
            throws Exception {
        http
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                // CSRF 토큰은 쿠키에 둔다 — 세션 저장소는 익명 GET마다 세션 행을 만든다.
                // JS는 메타 태그 값을 쓰므로 쿠키는 HttpOnly 그대로.
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository()))
                .authorizeHttpRequests(auth -> auth
                        // 기본은 인증 필요 — 공개 경로만 아래에 명시한다.
                        .requestMatchers("/", "/community", "/recommend", "/robots.txt", "/sitemap.xml",
                                "/login", "/signup", "/forgot-password",
                                "/users/password-reset", "/users/verify", "/users/verify/result",
                                "/healthz", "/readyz", "/error", "/error/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/posts/update/*").permitAll()
                        // 버전(빌드 SHA) 접두사가 붙은 정적 자원.
                        .requestMatchers(HttpMethod.GET, "/*/js/**", "/*/css/**", "/*/images/**").permitAll()
                        // 글쓰기 화면은 익명에게도 열려 있고 화면이 안내를 보여준다.
                        .requestMatchers("/posts/save").permitAll()
                        .requestMatchers("/api/v1/users").permitAll()
                        // 실제 경계는 메일로 보낸 1회용 토큰이다(PasswordResetService).
                        .requestMatchers("/api/v1/users/password-reset", "/api/v1/users/password-reset/confirm")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
                        // 댓글처럼 답글도 익명 열람을 허용한다.
                        .requestMatchers(HttpMethod.GET, "/api/v1/comments/*/replies").permitAll()
                        // 번호 추천은 공개 기능. 이 경로만 연다(CSRF는 그대로 검사한다).
                        .requestMatchers(HttpMethod.POST, "/api/v1/numbers/recommend").permitAll()
                        // 관리자 화면과 API를 같은 규칙으로 막는다.
                        .requestMatchers("/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated()
                )
                // 원래 요청을 세션에 저장하지 않는다(봇이 세션 행을 만들지 못하게). 복귀는 ?redirect=가 맡는다.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(redirectAwareSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(refererLogoutSuccessHandler())
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        // 외부 CDN·인라인 스크립트·스타일이 없어 'self'로 충분하다. img-src의 data:는
                        // favicon, blob:은 업로드 전 미리보기(useImageUpload.js)용.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self'; "
                                        + "style-src 'self'; "
                                        + "img-src 'self' data: blob:; "
                                        + "font-src 'self'; "
                                        + "connect-src 'self'; "
                                        + "form-action 'self'; "
                                        + "frame-ancestors 'self'; "
                                        + "base-uri 'self'; "
                                        + "object-src 'none'; "
                                        // HSTS 첫 방문 전에도 http: 링크를 https:로 바꿔 요청하게 한다.
                                        + "upgrade-insecure-requests"))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // 쓰지 않는 강력한 기능은 아예 막는다.
                        .permissionsPolicyHeader(permissions -> permissions
                                .policy("camera=(), microphone=(), geolocation=(), payment=()"))
                        // 다른 오리진 창과 window 참조를 끊는다(탭내빙 방지).
                        .crossOriginOpenerPolicy(coop -> coop
                                .policy(CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                        // 프록시 헤더 설정이 어긋나도 빠지지 않게 isSecure와 무관하게 항상 붙인다
                        // (평문 응답의 HSTS는 브라우저가 무시하므로 안전하다).
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .requestMatcher(request -> true))
                        // 색인할 이유가 없는 화면·API에 noindex. meta 대신 헤더라 JSON·오류 응답에도 붙는다.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                SecurityConfig::isNoindexPath,
                                new StaticHeadersWriter("X-Robots-Tag", "noindex, nofollow")))
                );

        return http.build();
    }

    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    /** 색인하지 않을 경로. "/users/"처럼 /로 끝나면 그 아래 전부, 아니면 그 경로와 그 하위. */
    private static final List<String> NOINDEX_PREFIXES = List.of(
            "/login", "/signup", "/forgot-password", "/users/", "/admin", "/posts/save", "/api/", "/error");

    static boolean isNoindexPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return NOINDEX_PREFIXES.stream().anyMatch(prefix -> prefix.endsWith("/")
                ? path.startsWith(prefix)
                : path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    /**
     * 로그인 후 {@code ?redirect=}(내부 경로만, {@link SafeRedirect#internalPath})로 돌아간다. 없거나
     * 로그인 화면이면 "/". 회원 번호를 세션에 심어 {@code SessionRevoker#revokeAll}이 계정을 구분하게 한다.
     */
    private AuthenticationSuccessHandler redirectAwareSuccessHandler() {
        return (request, response, authentication) -> {
            if (authentication.getPrincipal() instanceof KraftUserDetails principal) {
                request.getSession().setAttribute(SessionRevoker.USER_ID_SESSION_ATTRIBUTE, principal.getUserId());
            }

            String target = SafeRedirect.internalPath(request.getParameter("redirect"), "/");
            if (target.startsWith("/login")) {
                target = "/";
            }
            response.sendRedirect(target);
        };
    }

    /**
     * 로그아웃 후 {@code next}(내부 경로만) 또는 같은 오리진 Referer로 돌아간다. 글쓰기 화면이나
     * 외부 Referer면 "/"(오픈 리다이렉트 방지).
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
