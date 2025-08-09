package com.kraft.book.common.error;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        (a,b) -> a // first wins
                ));
        return ApiErrorResponse.of("VALIDATION_ERROR", "Validation failed", fieldErrors);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleBindException(BindException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        (a,b) -> a
                ));
        return ApiErrorResponse.of("VALIDATION_ERROR", "Validation failed", fieldErrors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return ApiErrorResponse.of("FORBIDDEN", "Access is denied");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiErrorResponse handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ApiErrorResponse.of("METHOD_NOT_ALLOWED", "Method not allowed");
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus // use status from exception
    public ApiErrorResponse handleResponseStatus(ResponseStatusException ex) {
        return ApiErrorResponse.of(ex.getStatusCode().toString(), ex.getReason() != null ? ex.getReason() : "Error");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return ApiErrorResponse.of("BAD_REQUEST", ex.getMessage() != null ? ex.getMessage() : "Bad request");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleOther(Exception ex) {
        return ApiErrorResponse.of("INTERNAL_ERROR", "Internal server error");
    }
}
