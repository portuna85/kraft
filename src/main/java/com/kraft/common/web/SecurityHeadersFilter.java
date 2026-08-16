package com.kraft.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// RequestIdFilter(HIGHEST_PRECEDENCE) 다음, OpsTokenFilter(5)를 포함한 나머지 필터보다 먼저 실행되어야
// 401/503 등 거부 응답에도 보안 헤더가 항상 붙는다. OpsTokenFilter와 동일한 @Order(5)를 쓰면 빈 이름
// 알파벳순이라는 우연에 기대게 되므로 명시적으로 앞당긴다.
//
// I-10: X-Content-Type-Options·X-Frame-Options·Referrer-Policy·Permissions-Policy·HSTS는
// caddy/Caddyfile이 사이트 전역으로 이미 발급한다(Caddy 자체가 만드는 403 등 앱이 못 보는
// 응답에도 필요해서). 이 필터가 같은 헤더를 다시 세팅하면 RFC 9110상 값이 이어붙거나
// (Cache-Control처럼) 두 소스가 따로 진화해 조용히 갈라진다(HSTS의 preload 유무 등).
// 그래서 여기서는 Caddy가 낼 수 없는 CSP만 다룬다 — 나머지는 Caddy가 단일 소스다.
@Component
@Order(2)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/h2-console") || uri.startsWith("/admin")) {
            // /admin은 AdminSecurityConfig가 자체 CSP(인라인 스타일 허용)를 적용한다.
            // 여기서 default-src 'none'을 강제하면 admin Thymeleaf 템플릿의 인라인 스타일이 차단된다.
            chain.doFilter(request, response);
            return;
        }
        // S-4: API/actuator 응답에 최소 CSP 적용 (브라우저 직접 접근 시 렌더링 표면 최소화)
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        chain.doFilter(request, response);
    }
}
