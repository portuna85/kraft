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
 * 로그인·가입·비밀번호 재설정·인증 메일 재발송에 IP(로그인은 계정도)별 분당 요청 제한을 건다({@link FixedWindowRateLimiter}).
 * {@code UsernamePasswordAuthenticationFilter}보다 먼저 등록해 무차별 대입이 인증 로직까지 가지 않게 한다. {@code enabled}가
 * false면(test·e2e 기본) 같은 IP에서 반복하는 환경이 흔들리지 않도록 모든 검사를 건너뛴다.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final String LOGIN_PATH = "/login";
    private static final String SIGNUP_PATH = "/api/v1/users";
    private static final String PASSWORD_RESET_PATH = "/api/v1/users/password-reset";
    private static final String RESEND_PATH = "/api/v1/users/me/verify-email/resend";
    /** 메일 링크의 이메일 인증 확인(폼 POST) — 토큰 추측은 비현실적이지만 무제한 시도는 막는다. */
    private static final String VERIFY_CONFIRM_PATH = "/users/verify";

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
            // IP 제한에 이미 걸렸으면 계정 제한기는 건드리지 않는다(매번 다른 username을 보내 맵을 무제한으로 키우지 못하게).
            if (ipOk) {
                String username = request.getParameter("username");
                if (username != null && !username.isBlank()) {
                    accountOk = loginAccountLimiter.tryAcquire(username.trim().toLowerCase(Locale.ROOT));
                }
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
        } else if (VERIFY_CONFIRM_PATH.equals(path) && !resendLimiter.tryAcquire("verify:" + ip)) {
            // 브라우저 폼이 보낸 요청이라 JSON이 아니라 공통 오류 화면(429)으로 보낸다.
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
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

    /** 다섯 제한기의 상태를 모두 처음으로 되돌린다. 테스트가 메서드 사이에 카운터를 남기지 않게 한다. */
    public void reset() {
        loginIpLimiter.reset();
        loginAccountLimiter.reset();
        signupLimiter.reset();
        passwordResetLimiter.reset();
        resendLimiter.reset();
    }

    /** 다섯 제한기의 허용/거부 집계를 주기적으로 남겨 한도 조정의 근거로 삼는다. */
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
