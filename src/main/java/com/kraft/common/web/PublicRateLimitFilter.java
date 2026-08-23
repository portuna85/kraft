package com.kraft.common.web;

import com.kraft.common.config.SecurityProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
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
 * trusted-proxy CIDR 내부 IP는 우회 처리 — 값은 KRAFT_SECURITY_TRUSTED_PROXY_CIDR
 * 환경변수(SecurityProperties.trustedProxyCidr)로 정해지며 로컬(docker-compose.yml)과
 * 운영(docker-compose.prod.yml)이 서로 다른 CIDR을 쓴다(운영은 RFC1918 전체가 기본값이라
 * I-11 조사 대상 중 하나 — 코드 예시 상수를 여기 박아두지 않는다). app 네트워크 서브넷을
 * compose의 networks.app.ipam.config로 고정해 이 CIDR을 좁히는 시도를 했으나(2026-08-16),
 * mariadb가 네트워크 재생성에서 누락돼 장애로 이어져 되돌렸다 — docker-compose.prod.yml의
 * 관련 주석 참고. 재설계: compose로 서브넷을 강제하지 않고 실제 운영 KRAFT_SECURITY_
 * TRUSTED_PROXY_CIDR 값을 .env.prod에서 직접 좁힌다(네트워크 재생성 불필요). 진단 로그는
 * 그 재배포로 헤더 부재가 실제로 해소되는지 확정 전까지 유지한다.
 * 실제 카운터 저장소는 RateLimitCounter(kraft.security.rate-limit-backend로 선택,
 * 기본 Caffeine/단일 인스턴스, redis 전환 시 다중 인스턴스 공유)에 위임한다.
 */
@Component
@Order(10)
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PublicRateLimitFilter.class);
    private static final int WINDOW_SECONDS = 60;
    private static final String KEY_PREFIX = "ratelimit:public:";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    /**
     * BE-SEC-01(docs/improvement.md): 초과 메트릭의 태그. raw 요청 경로 대신 이 고정 집합만
     * 태그로 쓴다 — 이전에는 {@code normalizePath}로 숫자 ID·UUID만 치환한 경로 문자열을
     * 그대로 태그로 등록해, 존재하지 않는 임의 경로로 429를 유발할 때마다 새 Micrometer
     * meter가 생겼다(무제한 카디널리티, JVM heap·scrape payload·Prometheus 저장 비용 증가).
     */
    enum Surface {
        OPS,
        COMMUNITY_WRITE,
        PUBLIC_API,
        UNKNOWN
    }

    private final SecurityProperties securityProperties;
    private final ClientIpResolver clientIpResolver;
    private final RateLimitCounter rateLimitCounter;
    private final ApiErrorResponseWriter apiErrorResponseWriter;
    private final Map<Surface, Counter> exceededCounters;

    public PublicRateLimitFilter(SecurityProperties securityProperties,
                                 ClientIpResolver clientIpResolver,
                                 RateLimitCounter rateLimitCounter,
                                 MeterRegistry meterRegistry,
                                 ApiErrorResponseWriter apiErrorResponseWriter) {
        this.securityProperties = securityProperties;
        this.clientIpResolver = clientIpResolver;
        this.rateLimitCounter = rateLimitCounter;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
        // TD-022 계열: 요청마다 Counter.builder(...).register(...)를 부르지 않고 고정 태그
        // 조합을 생성자에서 미리 등록한다. surface가 유한 집합이라 이 맵도 유한하다.
        this.exceededCounters = new EnumMap<>(Surface.class);
        for (Surface surface : Surface.values()) {
            exceededCounters.put(surface, Counter.builder("http.rate_limit.exceeded")
                    .tag("surface", surface.name().toLowerCase(Locale.ROOT))
                    .register(meterRegistry));
        }
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

        // I-11: 운영에서 X-RateLimit-* 헤더가 전혀 관측되지 않아 — 요청이 아래 두 조기 반환
        // 분기(신뢰 프록시 우회 / 카운터 백엔드 장애 fail-open) 중 어디로 빠지는지 확정하기
        // 전까지 임시로 남겨두는 진단 로그. 원인 확정 후(예: trustedProxyCidr 축소) 제거한다.
        if (log.isDebugEnabled()) {
            log.debug("Rate limit 분기 진단: remoteAddr={} xff={} resolvedIp={}",
                    request.getRemoteAddr(), request.getHeader("X-Forwarded-For"), clientIp);
        }

        if (clientIpResolver.isTrustedProxy(clientIp)) {
            // BE-SEC-02(docs/improvement.md): 프로덕션의 모든 요청은 Caddy(신뢰 대역)를 거치므로
            // 이 분기를 항상 탄다. application-prod.yml의 com.kraft=info에서는 log.info가
            // 억제되지 않아 요청마다 로그 한 줄이 남았다 — 앱 전체 최대 로그 발생원이었다.
            // I-11 진단은 debug로도 충분하다(바로 위 분기와 동일한 가드 패턴).
            if (log.isDebugEnabled()) {
                log.debug("Rate limit 우회(trusted proxy): remoteAddr={} xff={} resolvedIp={} path={}",
                        request.getRemoteAddr(), request.getHeader("X-Forwarded-For"), clientIp, request.getRequestURI());
            }
            chain.doFilter(request, response);
            return;
        }

        int limit = securityProperties.rateLimitPerMinute();
        OptionalInt current = rateLimitCounter.incrementAndGet(KEY_PREFIX + clientIp, WINDOW_SECONDS);

        if (current.isEmpty()) {
            // 카운터 백엔드(Redis) 장애 — fail-open, 이번 요청은 한도 검사 없이 통과.
            log.info("Rate limit 우회(카운터 백엔드 장애, fail-open): resolvedIp={} path={}",
                    clientIp, request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        int count = current.getAsInt();
        response.setIntHeader("X-RateLimit-Limit", limit);
        response.setIntHeader("X-RateLimit-Remaining", Math.max(0, limit - count));

        if (count > limit) {
            String path = request.getRequestURI();
            log.warn("Rate limit 초과: ip={} count={} limit={} path={}", clientIp, count, limit, path);
            exceededCounters.get(surfaceOf(request)).increment();
            response.setIntHeader("Retry-After", WINDOW_SECONDS);
            apiErrorResponseWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED", "요청 횟수가 너무 많습니다. 잠시 후 다시 시도하세요.");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * BE-SEC-01: 요청을 고정된 surface 하나로 분류한다. {@link #shouldNotFilter}가 이미
     * {@code /api/**}·{@code /ops/**}만 통과시키므로 {@link Surface#UNKNOWN}은 이론상
     * 도달하지 않지만, 향후 필터 적용 범위가 넓어져도 카디널리티가 무제한으로 늘지 않도록
     * 안전망으로 남긴다.
     */
    private static Surface surfaceOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/ops/")) {
            return Surface.OPS;
        }
        if (path.startsWith("/api/v1/community/") && WRITE_METHODS.contains(request.getMethod())) {
            return Surface.COMMUNITY_WRITE;
        }
        if (path.startsWith("/api/")) {
            return Surface.PUBLIC_API;
        }
        return Surface.UNKNOWN;
    }
}
