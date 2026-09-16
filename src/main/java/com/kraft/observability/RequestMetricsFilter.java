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

    /**
     * 알려진 한계: 아래 요청 밖으로 던져진 예외가 있으면 {@code response.getStatus()}는 이
     * {@code finally}가 도는 시점의 값을 읽는다. 서블릿 컨테이너가 예외를 실제 5xx 응답으로
     * 바꾸는 처리(스프링의 예외 → 상태 변환, {@code /error} 재디스패치 등)가 이 필터 바깥,
     * 더 나중에 일어날 수 있어 그 최종 상태를 여기서는 확정적으로 알 수 없다(개선 보고서
     * "관측값의 경계와 의미"). 정확히 맞추려면 상태를 추정하는 임시방편을 넣기보다 서블릿
     * 컨테이너·Spring MVC의 예외 처리 순서 자체를 다시 설계해야 하므로, 이번에는 한계로만
     * 남겨 둔다.
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
