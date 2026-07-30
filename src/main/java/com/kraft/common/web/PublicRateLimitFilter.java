package com.kraft.common.web;

import com.kraft.common.config.SecurityProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window(tumbling) rate limiter for public API endpoints — Caffeine의
 * {@code expireAfterWrite(1, MINUTES)}로 60초 텀블링 윈도우를 구현한다(슬라이딩이 아님).
 * 윈도우 경계에서 한도의 최대 ~2배 버스트가 가능하다.
 * trusted-proxy CIDR(172.28.0.0/16) 내부 IP는 우회 처리.
 * NOTE: Caffeine 기반 — 단일 인스턴스 전용. 수평 확장 시 Redis 전환 필요.
 */
@Component
@Order(10)
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PublicRateLimitFilter.class);
    private static final int WINDOW_SECONDS = 60;

    private final SecurityProperties securityProperties;
    private final ClientIpResolver clientIpResolver;
    private final Cache<String, AtomicInteger> counters;
    private final MeterRegistry meterRegistry;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public PublicRateLimitFilter(SecurityProperties securityProperties,
                                 ClientIpResolver clientIpResolver,
                                 MeterRegistry meterRegistry,
                                 ApiErrorResponseWriter apiErrorResponseWriter) {
        this.securityProperties = securityProperties;
        this.clientIpResolver = clientIpResolver;
        this.meterRegistry = meterRegistry;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(securityProperties.rateLimitMaxKeys())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // CORS preflight는 브라우저가 자동 발행하므로 한도를 소모하면 안 된다 —
        // 응답은 앞선 CorsFilter(order 1)가 처리한다.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.startsWith("/api/") && !path.startsWith("/ops/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String clientIp = clientIpResolver.resolve(request);

        if (clientIpResolver.isTrustedProxy(clientIp)) {
            chain.doFilter(request, response);
            return;
        }

        int limit = securityProperties.rateLimitPerMinute();
        int current = counters.get(clientIp, k -> new AtomicInteger(0)).incrementAndGet();

        response.setIntHeader("X-RateLimit-Limit", limit);
        response.setIntHeader("X-RateLimit-Remaining", Math.max(0, limit - current));

        if (current > limit) {
            String path = request.getRequestURI();
            log.warn("Rate limit 초과: ip={} count={} limit={} path={}", clientIp, current, limit, path);
            Counter.builder("http.rate_limit.exceeded")
                    .tag("path", normalizePath(path))
                    .register(meterRegistry)
                    .increment();
            response.setIntHeader("Retry-After", WINDOW_SECONDS);
            apiErrorResponseWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED", "요청 횟수가 너무 많습니다. 잠시 후 다시 시도하세요.");
            return;
        }

        chain.doFilter(request, response);
    }

    // Collapse dynamic segments (numeric IDs, UUIDs) to avoid high-cardinality labels.
    private static String normalizePath(String path) {
        return path.replaceAll("/\\d+", "/{id}")
                   .replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}", "/{uuid}");
    }
}
