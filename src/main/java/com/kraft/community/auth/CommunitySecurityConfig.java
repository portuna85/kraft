package com.kraft.community.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kraft.common.config.CommunityProperties;
import com.kraft.common.config.PublicBaseUrlProperties;
import com.kraft.common.error.ApiErrorResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 커뮤니티 OAuth2 로그인 체인. admin(@Order(1))보다 뒤, public API(@Order(2)→(3)으로
 * 재번호)보다 앞에 위치해야 /api/v1/community/**가 public 체인의 넓은 /api/** matcher에
 * 선점당하지 않는다. 세션 인증과 CookieCsrfTokenRepository double-submit을 사용한다.
 */
@Configuration
public class CommunitySecurityConfig {

    // CommunityAuthEntryPoint와 동일한 이유로 앱 전역 ObjectMapper 빈에 의존하지 않는다
    // (Boot 4.1 JacksonAutoConfiguration은 com.fasterxml.jackson.databind.ObjectMapper
    // 타입 빈을 노출하지 않는다) — 소규모 고정 DTO 직렬화만 필요하므로 로컬 인스턴스로 충분하다.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final CommunityOAuth2UserService communityOAuth2UserService;
    private final CommunityAuthEntryPoint communityAuthEntryPoint;
    private final CommunityProperties communityProperties;
    private final PublicBaseUrlProperties publicBaseUrlProperties;
    private final MeterRegistry meterRegistry;

    public CommunitySecurityConfig(CommunityOAuth2UserService communityOAuth2UserService,
                                    CommunityAuthEntryPoint communityAuthEntryPoint,
                                    CommunityProperties communityProperties,
                                    PublicBaseUrlProperties publicBaseUrlProperties,
                                    MeterRegistry meterRegistry) {
        this.communityOAuth2UserService = communityOAuth2UserService;
        this.communityAuthEntryPoint = communityAuthEntryPoint;
        this.communityProperties = communityProperties;
        this.publicBaseUrlProperties = publicBaseUrlProperties;
        this.meterRegistry = meterRegistry;
    }

    @Bean
    @Order(2)
    SecurityFilterChain communityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        return http
                .securityMatcher("/api/v1/community/**", "/oauth2/**", "/login/**", "/logout")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/community/session").permitAll()
                        .requestMatchers("/oauth2/**", "/login/**", "/logout").permitAll()
                        // 게시글/댓글 조회는 로그인 없이 공개, 쓰기(POST/PUT/DELETE)만 인증 요구.
                        .requestMatchers(HttpMethod.GET, "/api/v1/community/posts/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterAfter(new CommunityWriteRateLimitFilter(communityProperties), AuthorizationFilter.class)
                .addFilterAfter(csrfCookieFilter(), CsrfFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(communityOAuth2UserService))
                        .successHandler(successHandler())
                        .failureHandler(failureHandler()))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl(redirectTarget())
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(communityAuthEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .build();
    }

    // CSRF 거부율 관측 메트릭과 공통 오류 계약 —
    // CsrfException 외의 접근 거부도 동일한 ApiErrorResponse JSON으로 403을 내려보낸다.
    private AccessDeniedHandler accessDeniedHandler() {
        Counter csrfRejectedCounter = Counter.builder("kraft_community_csrf_rejected_total")
                .description("커뮤니티 체인에서 CSRF 토큰 불일치로 거부된 요청 수")
                .register(meterRegistry);
        return (request, response, accessDeniedException) -> {
            boolean isCsrf = accessDeniedException instanceof CsrfException;
            if (isCsrf) {
                csrfRejectedCounter.increment();
            }
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ApiErrorResponse body = new ApiErrorResponse(
                    Instant.now(),
                    HttpStatus.FORBIDDEN.value(),
                    HttpStatus.FORBIDDEN.getReasonPhrase(),
                    isCsrf ? "COMMUNITY_CSRF_REJECTED" : "COMMUNITY_ACCESS_DENIED",
                    isCsrf ? "요청을 검증할 수 없습니다. 새로고침 후 다시 시도하세요." : "접근이 거부되었습니다.",
                    request.getRequestURI());
            OBJECT_MAPPER.writeValue(response.getWriter(), body);
        };
    }

    // CookieCsrfTokenRepository는 토큰을 지연 로딩한다(Spring Security 6+) — Thymeleaf 폼처럼
    // ${_csrf}를 렌더링해 CsrfToken.getToken()을 호출하는 곳이 있어야 실제로 쿠키가 써진다.
    // 커뮤니티는 순수 JSON API(Next.js)라 그런 렌더링 지점이 없으므로, 이 필터가 매 요청마다
    // 강제로 getToken()을 호출해 XSRF-TOKEN 쿠키를 발급한다 — 없으면 프런트가 보낼 토큰이
    // 영영 생기지 않아 로그아웃·글쓰기·댓글 등 모든 쓰기 요청이 CSRF 403으로 거부된다.
    // (공식 가이드: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
    // "Javascript 애플리케이션" 섹션의 권장 패턴)
    private static OncePerRequestFilter csrfCookieFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (csrfToken != null) {
                    csrfToken.getToken();
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    private AuthenticationSuccessHandler successHandler() {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler(redirectTarget());
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }

    private AuthenticationFailureHandler failureHandler() {
        return new SimpleUrlAuthenticationFailureHandler(redirectTarget() + "?login_error=1");
    }

    // 프런트(Next.js)가 별도로 존재하므로(4단계 이식 예정) 로그인/로그아웃 완료 후에는
    // kraft.public-base-url로 돌려보낸다 — 로컬/테스트처럼 값이 없으면 "/"로 대체.
    private String redirectTarget() {
        String publicBaseUrl = publicBaseUrlProperties.publicBaseUrl();
        return (publicBaseUrl == null || publicBaseUrl.isBlank()) ? "/" : publicBaseUrl;
    }
}
