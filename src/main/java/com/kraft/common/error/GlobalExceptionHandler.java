package com.kraft.common.error;

import com.kraft.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // TD-004: 5xx는 내부/업스트림 원인을 담고 있을 수 있어 원문을 그대로 클라이언트에 보이면
    // 안 된다(정보 유출). code/status는 그대로 유지하고 메시지만 일반 문구로 교체한다.
    private static final String GENERIC_SERVER_ERROR_MESSAGE = "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        if (exception.getStatus().is5xxServerError()) {
            // handleUnexpected와 동일하게 throwable을 마지막 인자로 넘겨 스택트레이스가 로그에 남게 한다.
            log.error("API 예외: status={} code={} path={}",
                    exception.getStatus().value(),
                    exception.getCode(),
                    request.getRequestURI(),
                    exception);
            return ResponseEntity.status(exception.getStatus())
                    .body(errorBody(exception.getStatus(), exception.getErrorCode(), GENERIC_SERVER_ERROR_MESSAGE, request));
        }
        log.warn("API 예외: status={} code={} path={} message={}",
                exception.getStatus().value(),
                exception.getCode(),
                request.getRequestURI(),
                exception.getMessage());
        return ResponseEntity.status(exception.getStatus())
                .body(errorBody(exception.getStatus(), exception.getErrorCode(), exception.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                      HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().isEmpty()
                ? "입력값 검증에 실패했습니다."
                : exception.getBindingResult().getFieldErrors().stream()
                        .map(e -> e.getField() + ": " + e.getDefaultMessage())
                        .collect(Collectors.joining(", "));
        log.warn("검증 예외: path={} message={}", request.getRequestURI(), message);
        return ResponseEntity.badRequest()
                .body(errorBody(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, message, request));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException exception,
                                                         HttpServletRequest request) {
        if ("X-Device-Token".equals(exception.getHeaderName())) {
            return ResponseEntity.badRequest()
                    .body(errorBody(HttpStatus.BAD_REQUEST, ApiErrorCode.DEVICE_TOKEN_REQUIRED,
                            "X-Device-Token 헤더가 필요합니다.", request));
        }
        return ResponseEntity.badRequest()
                .body(errorBody(HttpStatus.BAD_REQUEST, ApiErrorCode.MISSING_HEADER,
                        exception.getHeaderName() + " 헤더가 필요합니다.", request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
                                                               HttpServletRequest request) {
        // M-11: exception.getMessage()에는 Java 프로퍼티 경로(예:
        // "list.arg1: must be greater than or equal to 0")가 그대로 들어 있어 내부 메서드
        // 시그니처 구조가 공개 API 응답으로 샌다. 원문은 로그에만 남기고, 응답에는 필드
        // 경로만(있으면) 안전하게 뽑아 노출한다 — handleHandlerMethodValidation과 같은 원칙.
        log.warn("제약 조건 위반: path={} message={}", request.getRequestURI(), exception.getMessage());
        String message = exception.getConstraintViolations().isEmpty()
                ? "입력값 검증에 실패했습니다."
                : exception.getConstraintViolations().stream()
                        .map(violation -> lastPathSegment(violation.getPropertyPath().toString())
                                + ": " + violation.getMessage())
                        .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(errorBody(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, message, request));
    }

    // "list.arg1" 같은 메서드 파라미터 경로에서 마지막 세그먼트("arg1")만 남긴다 — 전체
    // 경로는 어떤 서비스 메서드의 몇 번째 인자인지까지 드러내므로 그대로 노출하지 않는다.
    private static String lastPathSegment(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }

    // TD-003: 예전에는 여기서 모든 IllegalArgumentException을 400으로 내렸다(M-11).
    // 실제로 요청 파생 page/size는 이미 호출부(AdminController, CommunityPostService,
    // CommunityCommentService, RecommendationSetHistoryService, WinningNumberQueryService)에서
    // 클램프하고, enum 파싱도 이미 명시적으로 ApiException으로 감싸고 있어(CommunityPostController,
    // CommunityPostService, OpsService) 이 폴백에 기대는 정당한 호출부가 없다. 이 폴백을 남겨두면
    // 정말 예기치 못한 프로그래밍 결함(IAE)까지 400으로 위장해 5xx 지표와 스택트레이스를
    // 숨긴다 — 제거해 handleUnexpected(500 + 스택트레이스 로그)로 흐르게 한다.

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException exception,
                                                       HttpServletRequest request) {
        log.debug("요청 바디 파싱 실패: path={} message={}", request.getRequestURI(), exception.getMessage());
        return ResponseEntity.badRequest()
                .body(errorBody(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST_BODY, "요청 바디를 읽을 수 없습니다.", request));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception,
                                                                HttpServletRequest request) {
        log.debug("지원되지 않는 Content-Type: contentType={} path={}", exception.getContentType(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(errorBody(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
                        "지원되지 않는 Content-Type입니다.", request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException exception,
                                                           HttpServletRequest request) {
        log.debug("리소스를 찾을 수 없습니다: method={} path={} resource={}",
                exception.getHttpMethod(),
                request.getRequestURI(),
                exception.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "리소스를 찾을 수 없습니다.", request));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException exception,
                                                        HttpServletRequest request) {
        log.warn("필수 파라미터 누락: param={} path={}", exception.getParameterName(), request.getRequestURI());
        return ResponseEntity.badRequest()
                .body(errorBody(HttpStatus.BAD_REQUEST, ApiErrorCode.MISSING_PARAMETER,
                        exception.getParameterName() + " 파라미터가 필요합니다.", request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                        HttpServletRequest request) {
        // 거부된 값은 사용자 입력이며 토큰·식별자 등의 민감정보일 수 있으므로 로그에 남기지 않는다.
        log.warn("파라미터 타입 불일치: param={} path={}", exception.getName(), request.getRequestURI());
        return ResponseEntity.badRequest()
                .body(errorBody(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PARAMETER_TYPE,
                        exception.getName() + " 파라미터의 값이 올바르지 않습니다.", request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception,
                                                              HttpServletRequest request) {
        log.debug("지원되지 않는 메서드: method={} path={}", exception.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(h -> Optional.ofNullable(exception.getSupportedHttpMethods()).ifPresent(h::setAllow))
                .body(errorBody(HttpStatus.METHOD_NOT_ALLOWED, ApiErrorCode.METHOD_NOT_ALLOWED,
                        "지원되지 않는 HTTP 메서드입니다.", request));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException exception,
                                                         HttpServletRequest request) {
        log.debug("지원되지 않는 Accept 형식: path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(errorBody(HttpStatus.NOT_ACCEPTABLE, ApiErrorCode.NOT_ACCEPTABLE,
                        "요청한 Accept 형식으로 응답할 수 없습니다.", request));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException exception,
                                                                    HttpServletRequest request) {
        log.warn("컨트롤러 파라미터 검증 실패: path={} message={}", request.getRequestURI(), exception.getMessage());
        return ResponseEntity.badRequest()
                .body(errorBody(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, "입력값 검증에 실패했습니다.", request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("예상하지 못한 서버 예외 발생: path={}", request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR, "예상하지 못한 서버 오류가 발생했습니다.", request));
    }

    private ApiErrorResponse errorBody(HttpStatus status, ApiErrorCode code, String message, HttpServletRequest request) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code.name(),
                message,
                request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY)
        );
    }
}
