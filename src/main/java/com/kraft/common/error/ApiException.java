package com.kraft.common.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public ApiException(ApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ApiErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.status();
    }

    public String getCode() {
        return errorCode.name();
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }
}
