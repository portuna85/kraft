package com.kraft.community.auth;

import com.kraft.common.config.CommunityProperties;
import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.web.ApiErrorResponseWriter;
import com.kraft.common.web.RateLimitCounter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.http.HttpStatus;
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
 * 카운터 저장소는 RateLimitCounter(kraft.security.rate-limit-backend로 선택, PublicRateLimitFilter와
 * 동일한 빈 공유 — 접두어로 키 네임스페이스만 분리)에 위임한다.
 */
public class CommunityWriteRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE");
    private static final int WINDOW_SECONDS = 60;
    private static final String KEY_PREFIX = "ratelimit:community-write:";

    private final CommunityProperties communityProperties;
    private final RateLimitCounter rateLimitCounter;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public CommunityWriteRateLimitFilter(CommunityProperties communityProperties,
                                          RateLimitCounter rateLimitCounter,
                                          ApiErrorResponseWriter apiErrorResponseWriter) {
        this.communityProperties = communityProperties;
        this.rateLimitCounter = rateLimitCounter;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
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
        OptionalInt current = rateLimitCounter.incrementAndGet(KEY_PREFIX + userId, WINDOW_SECONDS);

        if (current.isEmpty()) {
            // 카운터 백엔드(Redis) 장애 — fail-open, 이번 요청은 한도 검사 없이 통과.
            chain.doFilter(request, response);
            return;
        }

        if (current.getAsInt() > limit) {
            response.setIntHeader("Retry-After", WINDOW_SECONDS);
            apiErrorResponseWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    ApiErrorCode.COMMUNITY_WRITE_RATE_LIMIT_EXCEEDED, "작성 요청이 너무 많습니다. 잠시 후 다시 시도하세요.");
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
