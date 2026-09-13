package com.kraft.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청 하나의 상태 코드와 걸린 시간을 {@link RequestMetrics}에 적는다.
 * <p>
 * 보안 필터 체인(order -100)보다 앞에 등록한다({@link ObservabilityConfig}). 뒤에 두면 인증 실패·CSRF 거부처럼 필터
 * 단계에서 끝나는 응답이 통계에 잡히지 않는다 — 세션이 통째로 깨져 403이 쏟아지는 상황이
 * 바로 알아야 할 상황인데, 그때 오히려 지표가 조용해진다.
 * <p>
 * 정적 자원은 세지 않는다. CSS·JS·이미지는 대부분 304로 끝나고 수가 압도적이라, 함께 세면
 * 실제 화면·API의 오류율이 묻혀 버린다 — 오류율을 보는 목적 자체가 사라진다.
 */
@RequiredArgsConstructor
public class RequestMetricsFilter extends OncePerRequestFilter {

    private final RequestMetrics metrics;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.equals("/favicon.ico");
    }

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
