package com.kraft.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return createErrorResponse(HttpStatus.FORBIDDEN, "접근이 거부되었습니다.", request.getRequestURI());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception ex, HttpServletRequest request) {
        log.warn("Resource not found: {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return createErrorResponse(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.", request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid argument: {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return createErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(Exception ex, HttpServletRequest request) {
        List<Map<String, String>> fieldErrors;
        if (ex instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException e = (MethodArgumentNotValidException) ex;
            fieldErrors = e.getBindingResult().getFieldErrors().stream()
                    .map(fe -> Map.of("field", fe.getField(), "rejectedValue", String.valueOf(fe.getRejectedValue()), "message", fe.getDefaultMessage()))
                    .collect(Collectors.toList());
        } else { // BindException
            BindException e = (BindException) ex;
            fieldErrors = e.getBindingResult().getFieldErrors().stream()
                    .map(fe -> Map.of("field", fe.getField(), "rejectedValue", String.valueOf(fe.getRejectedValue()), "message", fe.getDefaultMessage()))
                    .collect(Collectors.toList());
        }

        log.warn("Validation failed: {} {} - errors={} ", request.getMethod(), request.getRequestURI(), fieldErrors);

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.put("message", "입력 값 검증에 실패했습니다.");
        body.put("path", request.getRequestURI());
        body.put("timestamp", System.currentTimeMillis());
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBadJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed JSON: {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return createErrorResponse(HttpStatus.BAD_REQUEST, "요청 본문을 처리할 수 없습니다 (잘못된 JSON 등)", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {} {}: ", request.getMethod(), request.getRequestURI(), ex);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.", request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(HttpStatus status, String message, String path) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", message);
        errorResponse.put("path", path);
        errorResponse.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(status).body(errorResponse);
    }
}
