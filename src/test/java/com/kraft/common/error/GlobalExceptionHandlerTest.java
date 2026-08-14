package com.kraft.common.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.executable.ExecutableValidator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
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
            throw new ApiException(ApiErrorCode.ROUND_NOT_FOUND, "찾을 수 없습니다.");
        }

        @GetMapping("/test/server-error")
        void throwServerError() {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "서버 오류");
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

        @GetMapping("/test/constraint-violation")
        void throwConstraintViolation() {
            throw constraintViolationForNegativePage();
        }

        @GetMapping("/test/constraint-violation-empty")
        void throwEmptyConstraintViolation() {
            throw new ConstraintViolationException("빈 위반 집합", Set.of());
        }

        @GetMapping("/test/illegal-argument")
        void throwIllegalArgument() {
            // PageRequest.of(page, size)가 음수 page에 실제로 던지는 것과 같은 종류의 예외.
            throw new IllegalArgumentException("Page index must not be less than zero");
        }
    }

    // AdminController.rounds(int page, ...)처럼 메서드 파라미터 검증이 실패했을 때 실제
    // Bean Validation이 만드는 것과 같은 모양(propertyPath가 "메서드명.arg0")의
    // ConstraintViolationException을 재현한다 — 원문 메시지가 그대로 노출되면 이 구조(메서드
    // 시그니처)까지 API 응답에 드러난다는 것을 보여주기 위함이다.
    private static class Clampable {
        void page(@Min(0) int page) {}
    }

    // 실제 애플리케이션은 하드코딩된 한국어 메시지만 쓰지만, Hibernate Validator의 내장
    // 제약조건 메시지(@Min 등)만은 예외로 인터폴레이션 시점의 로케일을 따른다. JVM 기본
    // 로케일은 실행 환경(로컬 Windows vs CI Ubuntu 등)마다 달라 이 기본값에 기대면 테스트가
    // 환경에 따라 다른 메시지를 얻는다 — 여기서는 메시지 인터폴레이터를 한국어로 고정해
    // 결과가 결정적이게 만든다.
    private static ConstraintViolationException constraintViolationForNegativePage() {
        var configuration = Validation.byDefaultProvider().configure();
        var defaultInterpolator = configuration.getDefaultMessageInterpolator();
        Validator validator = configuration
                .messageInterpolator(new jakarta.validation.MessageInterpolator() {
                    @Override
                    public String interpolate(String messageTemplate, Context context) {
                        return defaultInterpolator.interpolate(messageTemplate, context, java.util.Locale.KOREAN);
                    }

                    @Override
                    public String interpolate(String messageTemplate, Context context, java.util.Locale locale) {
                        return defaultInterpolator.interpolate(messageTemplate, context, locale);
                    }
                })
                .buildValidatorFactory()
                .getValidator();
        ExecutableValidator executableValidator = validator.forExecutables();
        try {
            var method = Clampable.class.getDeclaredMethod("page", int.class);
            var violations = executableValidator.validateParameters(new Clampable(), method, new Object[] {-1});
            return new ConstraintViolationException(violations);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
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
    @DisplayName("TD-004: 5xx ApiException은 원문 메시지 대신 일반 문구를 반환하고 code/status는 유지한다")
    void handleApiException_returns5xxStatus_forServerError() throws Exception {
        mockMvc.perform(get("/test/server-error").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    @DisplayName("TD-004: 5xx ApiException은 로그에 원본 throwable(스택트레이스)을 남긴다")
    void handleApiException_5xx_logsThrowable() throws Exception {
        ch.qos.logback.classic.Logger handlerLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        Level previousLevel = handlerLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.setLevel(Level.ERROR);
        handlerLogger.addAppender(appender);

        try {
            mockMvc.perform(get("/test/server-error").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());
        } finally {
            handlerLogger.detachAppender(appender);
            handlerLogger.setLevel(previousLevel);
            appender.stop();
        }

        assertThat(appender.list)
                .anyMatch(event -> event.getThrowableProxy() != null
                        && "서버 오류".equals(event.getThrowableProxy().getMessage()));
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

    @Test
    @DisplayName("M-11: 제약 위반 응답은 필드명 기반의 안전한 메시지를 반환한다")
    void handleConstraintViolation_returnsFieldBasedMessage() throws Exception {
        mockMvc.perform(get("/test/constraint-violation").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("page: 0 이상이어야 합니다"));
    }

    @Test
    @DisplayName("제약 위반 집합이 비어 있으면 일반 검증 실패 메시지를 반환한다")
    void handleConstraintViolation_emptyViolations_returnsGenericMessage() throws Exception {
        mockMvc.perform(get("/test/constraint-violation-empty").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("입력값 검증에 실패했습니다."));
    }

    @Test
    @DisplayName("M-11: IllegalArgumentException(예: 음수 페이지)은 500이 아니라 400으로 응답한다")
    void handleIllegalArgument_returns400InsteadOf500() throws Exception {
        mockMvc.perform(get("/test/illegal-argument").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
    }
}
