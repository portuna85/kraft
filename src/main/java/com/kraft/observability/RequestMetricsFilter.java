package com.kraft.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청 하나의 상태 코드와 걸린 시간을 {@link RequestMetrics}에 적는다. 보안 필터 체인(order -100)보다 앞에 등록한다
 * ({@link ObservabilityConfig}) — 뒤에 두면 인증 실패·CSRF 거부처럼 필터 단계에서 끝나는 응답이 통계에서 빠진다.
 * <p>
 * 정적 자원은 세지 않는다(대부분 304라 수가 압도적이어서 실제 화면·API의 오류율이 묻힌다). 템플릿이 내보내는 JS는
 * {@code /{버전}/js/...}({@code spring.web.resources.chain.strategy.fixed})라 설정된 그 버전 하나만 인정한다 —
 * 임의의 {@code /무엇/js/}를 빼면 앱 경로의 오류까지 사라질 수 있다.
 */
public class RequestMetricsFilter extends OncePerRequestFilter {

    private final RequestMetrics metrics;
    /** {@code /{버전}/js/}. 고정 버전 전략을 쓰지 않으면 null. */
    private final String versionedJsPrefix;

    public RequestMetricsFilter(RequestMetrics metrics) {
        this(metrics, null);
    }

    /**
     * @param staticResourceVersion 정적 자원 고정 버전 문자열. 비어 있으면 버전 경로를 따로 보지 않는다.
     */
    public RequestMetricsFilter(RequestMetrics metrics, String staticResourceVersion) {
        this.metrics = metrics;
        this.versionedJsPrefix = staticResourceVersion == null || staticResourceVersion.isBlank()
                ? null
                : "/" + staticResourceVersion + "/js/";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.equals("/favicon.ico")
                || (versionedJsPrefix != null && path.startsWith(versionedJsPrefix));
    }

    /**
     * 알려진 한계: 요청 밖으로 던져진 예외는 서블릿 컨테이너/Spring이 5xx로 바꾸는 시점이 이 필터보다 나중일 수 있어,
     * {@code finally}에서 읽는 상태가 최종 값이 아닐 수 있다. 맞추려면 예외 처리 순서를 다시 설계해야 해 한계로만 둔다.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - startedAt) / 1_000_000;
            metrics.record(response.getStatus(), millis);
        }
    }
}
