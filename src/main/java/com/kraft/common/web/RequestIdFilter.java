package com.kraft.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String MDC_CLIENT_IP = "clientIp";
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    // 클라이언트가 보낸 값을 응답 헤더/로그에 그대로 반영하므로 CRLF 등 위험 문자를 차단한다(HRS_REQUEST_PARAMETER_TO_HTTP_HEADER).
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9\\-]{1,100}");

    private final ClientIpResolver clientIpResolver;

    public RequestIdFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        if (requestId == null || !SAFE_REQUEST_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }

        long startedAt = System.nanoTime();
        MDC.put(MDC_KEY, requestId);
        MDC.put(MDC_CLIENT_IP, clientIpResolver.resolve(request));
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            logRequest(request, response, elapsedMs);
            MDC.remove(MDC_KEY);
            MDC.remove(MDC_CLIENT_IP);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long elapsedMs) {
        int status = response.getStatus();
        String method = request.getMethod();
        String path = requestPath(request);
        // MDC에 이미 채워둔 resolved 클라이언트 IP를 그대로 쓴다 — request.getRemoteAddr()는
        // 리버스 프록시(Caddy) 뒤에서는 항상 프록시 컨테이너 IP라 로그 분석 시 혼동을 준다.
        String remote = MDC.get(MDC_CLIENT_IP);

        if (status >= 500) {
            log.error("HTTP {} {} -> status={} durationMs={} remote={}",
                    method, path, status, elapsedMs, remote);
        } else if (status >= 400) {
            log.warn("HTTP {} {} -> status={} durationMs={} remote={}",
                    method, path, status, elapsedMs, remote);
        } else {
            // 정상 트래픽은 운영 기본 레벨(INFO)에서 저장하지 않는다. 요청 ID·메서드·경로·
            // 상태·시간은 필요할 때 DEBUG로 볼 수 있고, 클라이언트 IP는 오류/거부 조사에만
            // 필요하므로 정상 요청 메시지에는 넣지 않는다.
            log.debug("HTTP {} {} -> status={} durationMs={}", method, path, status, elapsedMs);
        }
    }

    static String requestPath(HttpServletRequest request) {
        // OAuth callback query에는 일회용 authorization code와 state가 포함된다.
        // 모든 엔드포인트에서 query string을 제외해 인증 정보와 사용자 입력이 로그에 남지 않게 한다.
        return request.getRequestURI();
    }
}
