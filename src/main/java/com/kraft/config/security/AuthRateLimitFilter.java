package com.kraft.config.security;

import com.kraft.shared.web.FixedWindowRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Locale;

/**
 * 로그인·가입·비밀번호 재설정·인증 메일 재발송에 IP(로그인은 계정도 함께)별 분당 요청 제한을
 * 적용한다(개선 보고서 SEC-01). 추천 제한기와 같은 {@link FixedWindowRateLimiter}를 재사용한다.
 * {@code UsernamePasswordAuthenticationFilter}보다 먼저 등록해, 무차별 대입 시도가 인증 로직까지
 * 가지 않고 여기서 먼저 걸리게 한다({@code SecurityConfig}).
 * <p>
 * {@code app.auth.rate-limit.enabled}가 false면 모든 검사를 건너뛴다 — 테스트·E2E처럼 같은
 * IP에서 로그인을 반복하는 환경이 이 필터 때문에 흔들리지 않도록 test·e2e 프로파일에서는
 * 기본으로 꺼 둔다.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final String LOGIN_PATH = "/login";
    private static final String SIGNUP_PATH = "/api/v1/users";
    private static final String PASSWORD_RESET_PATH = "/api/v1/users/password-reset";
    private static final String RESEND_PATH = "/api/v1/users/me/verify-email/resend";

    private final boolean enabled;
    private final FixedWindowRateLimiter loginIpLimiter;
    private final FixedWindowRateLimiter loginAccountLimiter;
    private final FixedWindowRateLimiter signupLimiter;
    private final FixedWindowRateLimiter passwordResetLimiter;
    private final FixedWindowRateLimiter resendLimiter;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(
            boolean enabled,
            int loginPerMinute,
            int loginAccountPerMinute,
            int signupPerMinute,
            int passwordResetPerMinute,
            int resendPerMinute,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.loginIpLimiter = new FixedWindowRateLimiter("로그인(IP)", loginPerMinute, WINDOW_MILLIS);
        this.loginAccountLimiter = new FixedWindowRateLimiter("로그인(계정)", loginAccountPerMinute, WINDOW_MILLIS);
        this.signupLimiter = new FixedWindowRateLimiter("가입", signupPerMinute, WINDOW_MILLIS);
        this.passwordResetLimiter = new FixedWindowRateLimiter("비밀번호 재설정", passwordResetPerMinute, WINDOW_MILLIS);
        this.resendLimiter = new FixedWindowRateLimiter("인증 메일 재발송", resendPerMinute, WINDOW_MILLIS);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = pathOf(request);
        String ip = request.getRemoteAddr();

        if (LOGIN_PATH.equals(path)) {
            boolean ipOk = loginIpLimiter.tryAcquire(ip);
            boolean accountOk = true;
            String username = request.getParameter("username");
            if (username != null && !username.isBlank()) {
                accountOk = loginAccountLimiter.tryAcquire(username.trim().toLowerCase(Locale.ROOT));
            }
            if (!ipOk || !accountOk) {
                response.sendRedirect(request.getContextPath() + "/login?error=throttled");
                return;
            }
        } else if (SIGNUP_PATH.equals(path) && !signupLimiter.tryAcquire(ip)) {
            writeTooManyRequests(response);
            return;
        } else if (PASSWORD_RESET_PATH.equals(path) && !passwordResetLimiter.tryAcquire(ip)) {
            writeTooManyRequests(response);
            return;
        } else if (RESEND_PATH.equals(path) && !resendLimiter.tryAcquire(ip)) {
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        problem.setProperty("code", "AUTH_RATE_LIMITED");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    /**
     * 실측 없이는 한도가 맞는지 알 수 없다 — 다섯 제한기의 허용/거부 집계를 주기적으로 남긴다
     * (RecommendationRateLimiter와 같은 이유). 창이 비어 있어도(제한기를 끈 프로파일 포함)
     * 호출 비용은 무시할 만하다.
     */
    @Scheduled(fixedDelayString = "${app.auth.rate-limit.report-interval-ms:600000}")
    public void reportAndCleanup() {
        long now = System.currentTimeMillis();
        loginIpLimiter.reportAndCleanup(now);
        loginAccountLimiter.reportAndCleanup(now);
        signupLimiter.reportAndCleanup(now);
        passwordResetLimiter.reportAndCleanup(now);
        resendLimiter.reportAndCleanup(now);
    }
}
