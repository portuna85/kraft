package com.kraft.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kraft.common.error.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * KB-19: DispatcherServlet 이전(서블릿 필터·AuthenticationEntryPoint·AccessDeniedHandler)
 * 단계는 GlobalExceptionHandler(@ControllerAdvice)를 거치지 않아 ApiErrorResponse JSON을
 * 직접 써야 한다 — 이 필요가 있는 6곳이 각자 {@code new ObjectMapper().registerModule(...)}를
 * 들고 있었다. Boot 4.1 기본 JacksonAutoConfiguration이
 * {@code com.fasterxml.jackson.databind.ObjectMapper} 타입 빈을 노출하지 않아(내부적으로
 * 다른 JsonMapper 계열을 우선 구성) 앱 전역 빈을 재사용할 수 없다는 제약은 그대로 두고,
 * 로컬 ObjectMapper 인스턴스와 응답 작성 로직 자체를 이 클래스 하나로 모은다. 이 빈은
 * {@code @Component}라 CommunitySecurityConfig가 직접 {@code new}하는
 * CommunityWriteRateLimitFilter/CommunityWithdrawnAccountFilter에도 생성자 인자로
 * 넘겨 재사용한다.
 */
@Component
public class ApiErrorResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public void write(HttpServletRequest request, HttpServletResponse response,
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
        OBJECT_MAPPER.writeValue(response.getWriter(), body);
    }
}
