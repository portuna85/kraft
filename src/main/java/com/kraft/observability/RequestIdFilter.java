package com.kraft.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 요청마다 짧은 상관관계 id를 MDC에 넣어, 한 요청이 남긴 여러 로그 줄(컨트롤러·보안·SQL)을 묶는다(가상 스레드는 이름으로
 * 묶기 어렵다). {@code X-Request-Id}가 이미 있으면(프록시가 붙인 것) 그대로 써서 프록시 로그와 대조하고, 응답 헤더로도
 * 돌려준다. 형식 검증 없이 받으면 로그 줄마다 헤더 크기의 문자열이 복제되고 다른 요청의 id를 사칭할 수 있으므로, UUID 같은
 * 짧은 형식만 허용하고 나머지는 새로 만든 값으로 대체한다. {@link RequestMetricsFilter}보다 먼저 등록한다
 * ({@link ObservabilityConfig}).
 */
public class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Request-Id";
    static final String MDC_KEY = "requestId";

    /** UUID·짧은 영숫자 상관관계 id에 흔히 쓰이는 문자만 허용한다. */
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        if (requestId == null || !VALID_REQUEST_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 요청이 끝나면 명시적으로 지운다 — MDC가 스레드 로컬을 재사용하는 경로에 기대지 않는다.
            MDC.remove(MDC_KEY);
        }
    }
}
