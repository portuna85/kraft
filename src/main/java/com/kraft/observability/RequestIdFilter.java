package com.kraft.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 짧은 상관관계 id를 만들어 MDC에 넣는다(BE-28). 지금까지는 같은 요청이 남긴 여러
 * 로그 줄(컨트롤러 로그, 보안 로그, SQL 로그 등)을 시각·스레드 이름만으로 묶어야 해서,
 * 가상 스레드를 쓰는 이 앱에서는 특히 스레드 이름이 요청마다 다시 쓰이기 쉬워 뒤섞인 로그를
 * 한 요청 단위로 다시 추리기 어려웠다.
 * <p>
 * 요청 헤더({@code X-Request-Id})로 이미 상관관계 id를 갖고 있으면(리버스 프록시·로드밸런서가
 * 흔히 붙인다) 그 값을 그대로 쓴다 — 그래야 프록시 로그와도 같은 id로 대조할 수 있다. 없으면
 * 새로 만든다. 응답 헤더에도 그대로 돌려줘, 클라이언트·프록시가 "이 응답이 그 요청의
 * 응답이다"를 확인할 수 있게 한다.
 * <p>
 * {@link RequestMetricsFilter}보다 먼저(더 이른 순서로) 등록한다({@link ObservabilityConfig})
 * — 그래야 메트릭 필터를 포함해 이 요청이 지나가는 모든 로거가 이 id를 볼 수 있다.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Request-Id";
    static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 가상 스레드는 요청이 끝나도 스레드 자체가 풀로 돌아가 재사용되지 않지만(요청마다
            // 새로 만들어진다), 그래도 매 요청 끝에 명시적으로 지운다 — MDC 구현이 스레드
            // 로컬을 재사용하는 경로에 기대지 않기 위한 방어적 습관이다.
            MDC.remove(MDC_KEY);
        }
    }
}
