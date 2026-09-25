package com.kraft.config.security;

import com.kraft.shared.web.SafeRedirect;
import com.kraft.user.service.SessionRevoker;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
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

    /**
     * 로그인·가입·비밀번호 재설정·인증 메일 재발송의 요청 제한(개선 보고서 SEC-01). 별도 빈으로
     * 두어 {@code @Scheduled}(허용/거부 집계 보고)가 Spring에 의해 실행되게 한다.
     */
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

    /**
     * 일반 {@code Filter} 빈은 Spring Boot가 서블릿 컨테이너에도 자동 등록한다(기본
     * urlPatterns {@code /*}) — 아래 {@link #filterChain}이 이미 이 필터를 보안 체인의 정확한
     * 위치(UsernamePasswordAuthenticationFilter 앞)에 등록하므로, 서블릿 컨테이너 등록은
     * 같은 요청을 한 번 더(순서 보장 없이) 태우기만 할 뿐이다(개선 보고서 BE-30).
     * {@code OncePerRequestFilter}라 두 번째 실행은 조용히 no-op이지만, 의도치 않은 이중
     * 등록 자체를 막는다.
     */
    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(
            AuthRateLimitFilter authRateLimitFilter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(authRateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 정적 자원(CSS·JS·업로드 이미지) 전용 체인. 아래 {@link #filterChain}보다 먼저 매칭되도록
     * {@code @Order(0)}을 준다({@code securityMatcher}로 좁혀 둔 체인이 항상 먼저 검사되어야
     * 원래 체인의 catch-all에 걸리지 않는다).
     * <p>
     * 이 체인이 있는 이유는 캐싱이다. 기본 {@code HeadersConfigurer}는 모든 응답에
     * {@code Cache-Control: no-cache, no-store, max-age=0, must-revalidate}를 붙이는데, 이건
     * 로그인 상태가 섞여 나오는 페이지 응답에는 맞지만 CSS·JS·업로드 이미지처럼 사용자와 무관한
     * 정적 파일에는 맞지 않는다. 정적 리소스 핸들러가 세팅한 {@code Cache-Control}
     * (application.yml의 {@code spring.web.resources.cache.*})을 이 라이터가 덮어써 버려서
     * 브라우저가 매 페이지 이동마다 같은 파일을 다시 받고 있었다.
     * <p>
     * {@code web.ignoring()}으로 아예 필터 체인 밖에 두지 않는 이유는, 그러면
     * {@code X-Content-Type-Options}·{@code X-Frame-Options} 같은 나머지 보안 헤더까지 함께
     * 사라지기 때문이다. 여기서는 캐시 헤더 라이터만 끈다.
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
                .authorizeHttpRequests(auth -> auth
                        // 정적 자원(/css, /js, /images)은 위 staticResourceChain이 먼저 처리한다.
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/api/v1/users").permitAll()
                        // 비밀번호를 잊은 사람은 로그인할 수 없다. 이 두 경로만 열어 두고,
                        // 실제 경계는 메일로 보낸 1회용 토큰이 잡는다(PasswordResetService).
                        .requestMatchers("/api/v1/users/password-reset", "/api/v1/users/password-reset/confirm")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
                        // 답글 더 보기(개선 보고서 COR-05)도 게시글 목록·상세 GET과 같은
                        // 이유로 익명 열람을 허용한다 — 댓글 자체가 로그인 없이도 보이므로,
                        // 그 뒤에 숨어 있던 답글만 로그인해야 볼 수 있으면 어색하다.
                        .requestMatchers(HttpMethod.GET, "/api/v1/comments/*/replies").permitAll()
                        // 번호 추천 생성은 비저장·공개 기능이라 로그인 여부와 무관하게 동일하게
                        // 동작한다(02문서 7절). 이 경로 하나만 열고 /api/v1/numbers/** 전체를
                        // 미리 공개하지 않는다. permitAll은 CSRF 비활성화가 아니다 — 세션·폼
                        // 로그인·CSRF 정책은 그대로 유지된다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/numbers/recommend").permitAll()
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
                        // 이 앱은 전 화면이 자체 호스팅 CSS·JS만 쓴다(외부 CDN·폰트·인라인
                        // 스크립트·인라인 스타일이 전혀 없다 — F08 이후로 jQuery·Bootstrap도
                        // 직접 서빙한다) — 그래서 'self' 하나로 거의 모든 지시어를 막을 수
                        // 있다(개선 보고서 SEC-05). img-src에 data:와 blob:을 더한다 —
                        // favicon이 data: URI이고, 이미지 첨부 미리보기(useImageUpload.js)가
                        // 업로드 전 로컬 파일을 URL.createObjectURL로 만든 blob: URL로 보여준다
                        // (E2E post-image.spec.js가 이 CSP 위반을 실제로 잡아냈다). frame-ancestors는
                        // 위 frameOptions(SAMEORIGIN)의 CSP 버전으로, 오래된 브라우저를 위해
                        // X-Frame-Options와 함께 둔다.
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
                                        + "object-src 'none'"))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // TLS는 앞단 리버스 프록시가 종단한다. server.forward-headers-strategy를
                        // 아직 설정하지 않아(운영 프록시 설정을 확인한 뒤 SEC-01과 함께 정할
                        // 예정) request.isSecure()가 프록시를 못 거치면 항상 false일 수 있다 —
                        // 기본 매처(isSecure)를 쓰면 그 경우 HSTS가 영영 안 붙는다. 이 헤더는
                        // 평문 HTTP 응답에 실려도 브라우저가 무시하므로(사양상 보안 컨텍스트가
                        // 아니면 적용하지 않는다), 항상 붙이는 쪽이 안전하다.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .requestMatcher(request -> true))
                        // 검색 결과에 나올 이유가 없는 화면(로그인·가입·비밀번호·인증·관리자·
                        // 글쓰기)과 API 응답은 색인하지 않게 한다(평가 보고서 2026-09-25 F08).
                        // 템플릿 meta 대신 헤더로 붙이는 이유: 모델을 거치지 않는 응답(JSON·오류)
                        // 에도 같은 규칙이 적용되고, 경로 목록이 이 한 곳에 모인다.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                SecurityConfig::isNoindexPath,
                                new StaticHeadersWriter("X-Robots-Tag", "noindex, nofollow")))
                );

        return http.build();
    }

    /** 색인하지 않을 경로. "/users/"처럼 /로 끝나면 그 아래 전부, 아니면 그 경로와 그 하위. */
    private static final List<String> NOINDEX_PREFIXES = List.of(
            "/login", "/signup", "/forgot-password", "/users/", "/admin", "/posts/save", "/api/");

    static boolean isNoindexPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return NOINDEX_PREFIXES.stream().anyMatch(prefix -> prefix.endsWith("/")
                ? path.startsWith(prefix)
                : path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    /**
     * 로그인 폼(`/login`)이 어느 화면에서 왔는지는 네비게이션의 "로그인" 링크가 현재 경로를
     * {@code ?redirect=}로 실어 보내고, 로그인 폼의 히든 필드가 POST까지 그대로 옮겨온다.
     * 이 앱은 페이지 전체를 항상 {@code permitAll}로 열어두므로(보호된 화면에 접근했다가
     * 강제로 로그인 페이지로 튕겨나가는 경로가 없음) Spring Security 기본 {@code RequestCache}가
     * 채워질 일이 없다 — 그래서 그 메커니즘 대신 이 파라미터 하나로 직접 "원래 위치"를 구현한다.
     * 값이 앱 내부 경로로 확정되지 않거나({@link SafeRedirect#internalPath}) 로그인 화면 자기
     * 자신을 가리키면 기본값 "/"로 이동한다.
     * <p>
     * 로그인 성공 시 회원 번호를 세션 속성으로 심는다(B02) — {@code SessionRevoker#revokeAll}이
     * 탈퇴 후 같은 이메일로 재가입한 다른 계정의 세션과 구분하는 데 쓴다.
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
