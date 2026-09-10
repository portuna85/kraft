package com.kraft.web.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * REST API({@code com.kraft.web.api} 패키지) 전용 전역 예외 처리기.
 * <p>
 * 화면 컨트롤러({@code IndexController})는 이 범위에 포함하지 않는다. 브라우저가 렌더링하는
 * Thymeleaf 뷰 요청에 JSON 오류 응답을 내려주는 것은 적절하지 않기 때문이다.
 * <p>
 * Spring Boot 4가 지원하는 {@link ProblemDetail}(RFC 9457)을 그대로 반환한다 — 컨트롤러/
 * {@code @ExceptionHandler} 메서드가 {@code ProblemDetail}을 반환하면 프레임워크가 상태 코드와
 * {@code Content-Type: application/problem+json}을 자동으로 설정하므로 별도 래퍼 클래스나
 * 신규 의존성이 필요 없다.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.kraft.web.api")
public class ApiExceptionHandler {

    /**
     * 서비스 계층에서 "대상을 찾을 수 없음", "이미 존재함" 등의 검증 실패를 나타낼 때 사용하는
     * {@link IllegalArgumentException}을 400으로 변환한다.
     * (예: 존재하지 않는 게시글/회원 조회, 이메일 중복 가입)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * {@code @Valid @RequestBody} 검증 실패(예: 빈 제목)를 400으로 변환하고,
     * 필드별 오류 메시지를 하나의 문자열로 모아 반환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 요청 본문이 JSON으로 파싱되지 않는 경우(형식 오류, 잘못된 인코딩 등) 400으로 변환한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedJson(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다.");
    }

    /**
     * 경로 변수 타입 변환 실패(예: {@code GET /api/v1/posts/{id}}에 숫자가 아닌 값이 들어온 경우)를
     * 400으로 변환한다. 이 핸들러가 없으면 catch-all({@link #handleUnexpected})에 잡혀 500이
     * 되는데, 클라이언트 잘못으로 인한 요청 형식 오류이므로 400이 더 적절하다.
     * (실측: {@code /api/v1/posts/list}처럼 옛 목록 조회 경로를 호출하면 {@code {id}}에 "list"가
     * 매칭되어 이 예외가 발생한다 — 05장 5.3.5절의 경로 변경(P2-11) 검증 중 실제로 발견했다.)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "요청 값의 형식이 올바르지 않습니다: " + e.getName());
    }

    /**
     * 작성자 본인/관리자가 아닌 사용자의 수정·삭제 시도({@code PostService.validateOwner()})를
     * 403으로 변환한다. 이 핸들러가 없으면 Spring Security의 {@code ExceptionTranslationFilter}가
     * 대신 처리하지만(그 경우도 403), 이 애노테이션이 있으면 다른 오류들과 동일한 JSON 형식으로
     * 통일된다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /**
     * 위에서 처리하지 못한 나머지 모든 예외의 최종 방어선. 클라이언트에는 상세 원인을 노출하지
     * 않고, 서버 로그에는 전체 스택 트레이스를 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외가 발생했습니다.", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }
}
