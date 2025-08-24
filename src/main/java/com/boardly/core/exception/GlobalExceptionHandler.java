package com.boardly.core.exception;

import com.boardly.core.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(e.getMessage(), null));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<?>> handleValidation(Exception e) {
        String msg = "Validation error";
        if (e instanceof MethodArgumentNotValidException manv) {
            msg = manv.getBindingResult().getFieldErrors().stream()
                    .map(fe -> fe.getField()+": "+fe.getDefaultMessage())
                    .findFirst().orElse(msg);
        }
        return ResponseEntity.badRequest().body(new ApiResponse<>(msg, null));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleUserNotFound(UsernameNotFoundException e) {
        return ResponseEntity.status(404).body(new ApiResponse<>(e.getMessage(), null));
    }
}
