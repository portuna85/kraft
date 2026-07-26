package com.kraft.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kraft.common.config.OpsProperties;
import com.kraft.common.error.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * B-02: 이 필터는 DispatcherServlet 이전(서블릿 필터 단계)에서 거부를 결정하므로
 * GlobalExceptionHandler를 거치지 않는다 — CommunityAuthEntryPoint와 동일하게 같은
 * ApiErrorResponse 계약(timestamp/status/error/code/message/path)을 직접 만들어
 * 응답한다. 전에는 {code, message}만 담은 손으로 쓴 JSON이라 나머지 API와 계약이
 * 달랐다.
 */
@Component
@Order(5)
public class OpsTokenFilter extends OncePerRequestFilter {

    // 앱 전역 ObjectMapper 빈에 의존하지 않는다 — Boot 4.1 기본 JacksonAutoConfiguration은
    // com.fasterxml.jackson.databind.ObjectMapper 타입 빈을 노출하지 않는다. 이 클래스는
    // 소규모 고정 DTO 직렬화만 필요하므로 로컬 인스턴스로 충분하다(CommunityAuthEntryPoint와 동일 패턴).
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final OpsProperties opsProperties;

    public OpsTokenFilter(OpsProperties opsProperties) {
        this.opsProperties = opsProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/ops");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String expected = opsProperties.token();
        if (expected == null || expected.isBlank()) {
            writeError(request, response, HttpStatus.SERVICE_UNAVAILABLE, "OPS_DISABLED",
                    "운영 API 토큰이 설정되지 않았습니다.");
            return;
        }
        String token = request.getHeader("X-Ops-Token");
        if (token == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            writeError(request, response, HttpStatus.UNAUTHORIZED, "OPS_UNAUTHORIZED",
                    "운영 API 인증에 실패했습니다.");
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
