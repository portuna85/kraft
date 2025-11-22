package com.kraft.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 일관된 형식으로 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Validation 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Validation 예외 발생: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "입력 값이 올바르지 않습니다",
                errors
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 비즈니스 로직 예외 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("비즈니스 로직 예외 발생: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 리소스를 찾을 수 없는 예외 처리
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("리소스 없음 예외 발생: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 중복 리소스 예외 처리
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResourceException(DuplicateResourceException ex) {
        log.warn("중복 리소스 예외 발생: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException ex) {
        log.warn("인증 실패 예외 발생: {}", ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * 모든 예외의 최종 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("예상치 못한 예외 발생", ex);

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 에러 응답 DTO
     */
    public record ErrorResponse(
            int status,
            String message,
            LocalDateTime timestamp,
            Map<String, String> errors
    ) {
        public static ErrorResponse of(HttpStatus status, String message) {
            return new ErrorResponse(
                    status.value(),
                    message,
                    LocalDateTime.now(),
                    null
            );
        }

        public static ErrorResponse of(HttpStatus status, String message, Map<String, String> errors) {
            return new ErrorResponse(
                    status.value(),
                    message,
                    LocalDateTime.now(),
                    errors
            );
        }
    }
}

