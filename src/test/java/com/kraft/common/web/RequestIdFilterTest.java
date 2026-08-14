package com.kraft.common.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("RequestIdFilter 요청 경로 로깅")
class RequestIdFilterTest {

    @Test
    @DisplayName("OAuth code와 state가 포함된 query string을 로그 경로에서 제외한다")
    void excludesQueryStringFromLoggedPath() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/login/oauth2/code/naver");

        String path = RequestIdFilter.requestPath(request);

        assertThat(path).isEqualTo("/login/oauth2/code/naver");
        verify(request, never()).getQueryString();
    }

    @Test
    @DisplayName("정상 요청은 DEBUG로 기록하고 클라이언트 IP는 메시지에서 제외한다")
    void successfulRequest_logsAtDebugWithoutClientIp() throws Exception {
        ClientIpResolver resolver = mock(ClientIpResolver.class);
        RequestIdFilter filter = new RequestIdFilter(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rounds/latest");
        request.addHeader(RequestIdFilter.HEADER_NAME, "safe-request-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(resolver.resolve(request)).thenReturn("203.0.113.9");
        CapturedLogger captured = captureLogs(Level.DEBUG);

        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                    ((MockHttpServletResponse) servletResponse).setStatus(200));
        } finally {
            captured.close();
        }

        assertThat(captured.appender().list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage())
                    .contains("GET", "/api/v1/rounds/latest", "status=200")
                    .doesNotContain("203.0.113.9", "remote=");
            assertThat(event.getMDCPropertyMap()).containsEntry(RequestIdFilter.MDC_KEY, "safe-request-id");
        });
    }

    @Test
    @DisplayName("오류 요청은 WARN으로 기록하되 query string은 남기지 않는다")
    void clientError_logsAtWarnWithoutQueryString() throws Exception {
        ClientIpResolver resolver = mock(ClientIpResolver.class);
        RequestIdFilter filter = new RequestIdFilter(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/community/posts");
        request.setQueryString("token=must-not-be-logged");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(resolver.resolve(request)).thenReturn("203.0.113.10");
        CapturedLogger captured = captureLogs(Level.DEBUG);

        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                    ((MockHttpServletResponse) servletResponse).setStatus(400));
        } finally {
            captured.close();
        }

        assertThat(captured.appender().list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("remote=203.0.113.10")
                    .doesNotContain("must-not-be-logged", "token=");
        });
    }

    private static CapturedLogger captureLogs(Level level) {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RequestIdFilter.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(level);
        logger.addAppender(appender);
        return new CapturedLogger(logger, previousLevel, appender);
    }

    private record CapturedLogger(ch.qos.logback.classic.Logger logger, Level previousLevel,
                                  ListAppender<ILoggingEvent> appender) implements AutoCloseable {
        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
