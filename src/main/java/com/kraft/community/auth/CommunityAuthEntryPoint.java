package com.kraft.community.auth;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.web.ApiErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * community 체인은 Next.js가 별도로 페이지를 서빙하므로(백엔드는 API만 응답), 미인증 요청은
 * 항상 401 JSON으로 응답한다 — 로그인 페이지로의 리다이렉트는 프런트 라우팅(4단계)이 담당한다.
 * GlobalExceptionHandler와 동일한 ApiErrorResponse 계약을 쓰지만, 이 지점은
 * ExceptionTranslationFilter가 DispatcherServlet 이전에 처리하므로 직접 응답을 작성한다.
 */
@Component
public class CommunityAuthEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public CommunityAuthEntryPoint(ApiErrorResponseWriter apiErrorResponseWriter) {
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        apiErrorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                ApiErrorCode.COMMUNITY_LOGIN_REQUIRED, "로그인이 필요합니다.");
    }
}
