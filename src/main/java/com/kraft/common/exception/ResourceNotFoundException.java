package com.kraft.common.exception;

/**
 * 리소스를 찾을 수 없을 때 발생하는 예외
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(String.format("%s를 찾을 수 없습니다: id=%d", resourceName, id));
    }

    public ResourceNotFoundException(String resourceName, String identifier) {
        super(String.format("%s를 찾을 수 없습니다: %s", resourceName, identifier));
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s를 찾을 수 없습니다: %s=%s", resourceName, fieldName, fieldValue));
    }
}
