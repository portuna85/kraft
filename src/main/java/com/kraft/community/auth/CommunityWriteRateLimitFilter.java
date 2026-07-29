package com.kraft.community.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kraft.common.config.CommunityProperties;
import com.kraft.common.error.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * community 쓰기(POST/PUT/DELETE)에 대해 PublicRateLimitFilter(IP 키, 공개 조회용)보다
 * 엄격한 사용자 ID 키 한도를 추가로 건다. 이 필터는
 * Spring Security 체인 내부에 등록되어 인증 이후(AuthorizationFilter 다음)에 실행되므로
 * SecurityContext에서 CommunityPrincipal을 안전하게 읽을 수 있다.
 *
 * 의도적으로 @Component가 아니다 — Spring Boot의 FilterRegistrationBean 자동 등록 대상이 되면
 * 전역 서블릿 필터로 한 번 더 실행되어(AdminSecurityConfig의 adminLoginCsrfRedirectFilter와
 * 동일한 이유) 카운트가 두 번 증가한다. CommunitySecurityConfig가 직접 new로 생성해 체인에만
 * 끼워 넣는다.
 * NOTE: Caffeine 기반 — 단일 인스턴스 전용. 인스턴스마다 독립된 카운터를 가지므로 수평
 * 확장 시 사용자당 실효 한도가 인스턴스 수만큼 배로 늘어난다(로드밸런서가 같은 사용자를
 * 매번 다른 인스턴스로 보낼 경우). 인스턴스 간 공유가 필요해지면 Redis 전환 필요.
 */
public class CommunityWriteRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE");

    // CommunityAuthEntryPoint/CommunitySecurityConfig와 동일한 이유로 앱 전역 ObjectMapper
    // 빈에 의존하지 않고 로컬 인스턴스를 쓴다.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Cache<Long, AtomicInteger> counters;
    private final CommunityProperties communityProperties;

    public CommunityWriteRateLimitFilter(CommunityProperties communityProperties) {
        this.communityProperties = communityProperties;
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        Long userId = currentUserId();
        if (userId == null) {
            // 미인증 요청은 뒤이은 authorizeHttpRequests 규칙이 401로 거부한다 — 여기서는 통과.
            chain.doFilter(request, response);
            return;
        }

        int limit = communityProperties.writeRateLimitPerMinute();
        int current = counters.get(userId, id -> new AtomicInteger(0)).incrementAndGet();
        if (current > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setIntHeader("Retry-After", 60);
            ApiErrorResponse body = new ApiErrorResponse(
                    Instant.now(),
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    "COMMUNITY_WRITE_RATE_LIMIT_EXCEEDED",
                    "작성 요청이 너무 많습니다. 잠시 후 다시 시도하세요.",
                    request.getRequestURI());
            OBJECT_MAPPER.writeValue(response.getWriter(), body);
            return;
        }

        chain.doFilter(request, response);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CommunityPrincipal principal) {
            return principal.getUserId();
        }
        return null;
    }
}
