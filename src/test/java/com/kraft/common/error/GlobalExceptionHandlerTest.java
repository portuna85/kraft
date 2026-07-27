package com.kraft.common.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("글로벌 예외 핸들러 테스트")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class TestController {
        @GetMapping("/test/not-found")
        void throwNotFound() {
            throw new ApiException(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "찾을 수 없습니다.");
        }

        @GetMapping("/test/server-error")
        void throwServerError() {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류");
        }

        @GetMapping("/test/unexpected")
        void throwUnexpected() {
            throw new RuntimeException("unexpected boom");
        }

        @GetMapping("/test/no-resource")
        void throwNoResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/test/no-resource", null);
        }

        record BodyDto(String value) {}

        @PostMapping(value = "/test/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        void acceptBody(@RequestBody BodyDto body) {}

        @GetMapping("/test/typed")
        void acceptTypedValue(@RequestParam Integer number) {}
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("애플리케이션 예외 발생 시 올바른 클라이언트 오류 상태 코드와 응답 바디를 반환하는지 확인")
    void handleApiException_returnsCorrectStatusAndBody_for4xx() throws Exception {
        mockMvc.perform(get("/test/not-found").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ROUND_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("찾을 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/test/not-found"));
    }

    @Test
    @DisplayName("애플리케이션 예외 발생 시 서버 오류 계열 상태 코드를 반환하는지 확인")
    void handleApiException_returns5xxStatus_forServerError() throws Exception {
        mockMvc.perform(get("/test/server-error").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류"));
    }

    @Test
    @DisplayName("리소스를 찾을 수 없을 때 404 상태 코드를 반환하는지 확인")
    void handleNoResourceFound_returns404() throws Exception {
        mockMvc.perform(get("/test/no-resource").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/test/no-resource"));
    }

    @Test
    @DisplayName("예상치 못한 예외 발생 시 500 상태 코드를 반환하는지 확인")
    void handleUnexpected_returns500() throws Exception {
        mockMvc.perform(get("/test/unexpected").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("예상하지 못한 서버 오류가 발생했습니다."));
    }

    @Test
    @DisplayName("잘못된 요청 본문 전송 시 400 상태 코드를 반환하는지 확인")
    void handleNotReadable_returns400() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("지원되지 않는 콘텐츠 유형 사용 시 415 상태 코드를 반환하는지 확인")
    void handleUnsupportedMediaType_returns415() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    @DisplayName("파라미터 타입 불일치 로그에는 거부된 사용자 입력을 남기지 않는다")
    void handleTypeMismatch_doesNotLogRejectedValue() throws Exception {
        ch.qos.logback.classic.Logger handlerLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        Level previousLevel = handlerLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.setLevel(Level.WARN);
        handlerLogger.addAppender(appender);

        try {
            mockMvc.perform(get("/test/typed").param("number", "sensitive-value"))
                    .andExpect(status().isBadRequest());
        } finally {
            handlerLogger.detachAppender(appender);
            handlerLogger.setLevel(previousLevel);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("param=number"))
                .noneMatch(message -> message.contains("sensitive-value"));
    }
}
