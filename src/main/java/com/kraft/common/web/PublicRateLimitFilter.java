package com.kraft.common.web;

import com.kraft.common.config.SecurityProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window(tumbling) rate limiter for public API endpoints — 60초 텀블링 윈도우를
 * 구현한다(슬라이딩이 아님). 윈도우 경계에서 한도의 최대 ~2배 버스트가 가능하다.
 * trusted-proxy CIDR(172.28.0.0/16) 내부 IP는 우회 처리.
 * 실제 카운터 저장소는 RateLimitCounter(kraft.security.rate-limit-backend로 선택,
 * 기본 Caffeine/단일 인스턴스, redis 전환 시 다중 인스턴스 공유)에 위임한다.
 */
@Component
@Order(10)
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PublicRateLimitFilter.class);
    private static final int WINDOW_SECONDS = 60;
    private static final String KEY_PREFIX = "ratelimit:public:";

    private final SecurityProperties securityProperties;
    private final ClientIpResolver clientIpResolver;
    private final RateLimitCounter rateLimitCounter;
    private final MeterRegistry meterRegistry;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public PublicRateLimitFilter(SecurityProperties securityProperties,
                                 ClientIpResolver clientIpResolver,
                                 RateLimitCounter rateLimitCounter,
                                 MeterRegistry meterRegistry,
                                 ApiErrorResponseWriter apiErrorResponseWriter) {
        this.securityProperties = securityProperties;
        this.clientIpResolver = clientIpResolver;
        this.rateLimitCounter = rateLimitCounter;
        this.meterRegistry = meterRegistry;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
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
        OptionalInt current = rateLimitCounter.incrementAndGet(KEY_PREFIX + clientIp, WINDOW_SECONDS);

        if (current.isEmpty()) {
            // 카운터 백엔드(Redis) 장애 — fail-open, 이번 요청은 한도 검사 없이 통과.
            chain.doFilter(request, response);
            return;
        }

        int count = current.getAsInt();
        response.setIntHeader("X-RateLimit-Limit", limit);
        response.setIntHeader("X-RateLimit-Remaining", Math.max(0, limit - count));

        if (count > limit) {
            String path = request.getRequestURI();
            log.warn("Rate limit 초과: ip={} count={} limit={} path={}", clientIp, count, limit, path);
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
