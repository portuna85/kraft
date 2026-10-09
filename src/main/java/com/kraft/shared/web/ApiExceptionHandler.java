package com.kraft.shared.web;

import com.kraft.recommend.domain.RecommendationGenerationLimitException;
import com.kraft.recommend.domain.RecommendationHistoryNotReadyException;
import com.kraft.recommend.domain.RecommendationValidationException;
import com.kraft.shared.exception.BusinessValidationException;
import com.kraft.shared.domain.PreconditionFailedException;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.shared.exception.PreconditionRequiredException;
import com.kraft.shared.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * {@link RestController} 전용 전역 예외 처리기. 화면(Thymeleaf) 요청은 다루지 않는다.
 * <p>
 * {@link ResponseEntityExceptionHandler}를 상속해 표준 MVC 예외를 제 상태 코드로 돌려준다.
 * 부모가 이미 매핑한 타입은 {@code @ExceptionHandler}를 새로 달면 "Ambiguous @ExceptionHandler"로
 * 기동에 실패하므로 부모 메서드를 오버라이드한다.
 */
@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** 서비스의 입력·상태 검증 실패(400). 메시지 끝 내부 식별자({@code " id=123"})는 응답에서 자른다. */
    @ExceptionHandler(BusinessValidationException.class)
    public ProblemDetail handleBusinessValidation(BusinessValidationException e) {
        String message = e.getMessage();
        log.debug("검증 실패: {}", message);
        // 사용자가 입력한 값(name= 등)은 그대로 둔다 — 내부 식별자(id=, userId=)만 자른다.
        String detail = message == null ? null : TRAILING_INTERNAL_ID.matcher(message).replaceFirst("");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /** 그 밖의 {@link IllegalArgumentException}은 의도한 검증이 아니라 프로그래밍 오류로 보고 500. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        log.error("검증 실패 예외가 아닌 IllegalArgumentException입니다 — 프로그래밍 오류일 수 있습니다.", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }

    /** 디스크 읽기·쓰기·삭제 실패. 서버 환경 문제라 500(5xx 경보에 잡히게). */
    @ExceptionHandler(StorageException.class)
    public ProblemDetail handleStorage(StorageException e) {
        log.error("파일 저장소 처리에 실패했습니다.", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }

    /** "대상 없음"(404). 메시지 끝 내부 식별자는 응답에서 자른다. */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        log.debug("대상을 찾을 수 없습니다: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, stripIdentifier(e.getMessage()));
    }

    private static String stripIdentifier(String message) {
        return message == null ? "대상을 찾을 수 없습니다." : TRAILING_IDENTIFIER.matcher(message).replaceFirst("");
    }

    /**
     * 번호 추천 요청 검증 실패(400). {@link IllegalArgumentException} 하위 타입이지만 더 구체적인
     * 이 핸들러가 우선하며, {@code code} 확장 속성을 싣는다.
     */
    @ExceptionHandler(RecommendationValidationException.class)
    public ProblemDetail handleRecommendationValidation(RecommendationValidationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("code", e.getCode());
        return problem;
    }

    /** 검증된 당첨 이력이 준비되지 않았거나 생성 도중 바뀜(503). 추천 API만 실패시킨다. */
    @ExceptionHandler(RecommendationHistoryNotReadyException.class)
    public ProblemDetail handleRecommendationHistoryNotReady(RecommendationHistoryNotReadyException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setProperty("code", RecommendationHistoryNotReadyException.CODE);
        return problem;
    }

    /**
     * 가능은 하지만 반복 상한 안에 요청 개수를 채우지 못함(503). 수학적으로 불가능한 경우
     * ({@code INSUFFICIENT_UNIQUE_COMBINATIONS})와 구분한다.
     */
    @ExceptionHandler(RecommendationGenerationLimitException.class)
    public ProblemDetail handleRecommendationGenerationLimit(RecommendationGenerationLimitException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setProperty("code", RecommendationGenerationLimitException.CODE);
        return problem;
    }

    /** {@code @Valid} 검증 실패(400). detail에는 첫 메시지만, 필드별 오류는 {@code errors}에 싣는다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldErrorDto(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        // detail에 필드명을 붙이지 않는다 — 내부 필드명이 사용자에게 보이지 않게.
        String detail = errors.isEmpty() ? "입력값을 확인해 주세요." : errors.get(0).message();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** {@code errors[]} 확장 속성 한 항목. 화면이 입력칸 옆에 붙일 수 있게 필드명과 메시지를 나눠 싣는다. */
    public record FieldErrorDto(String field, String message) {
    }

    /** 본문을 JSON으로 읽을 수 없음(400). */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다."));
    }

    /** 경로 변수 타입 변환 실패(예: {@code /api/v1/posts/list}의 "list"). 클라이언트 잘못이라 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "요청 값의 형식이 올바르지 않습니다: " + e.getName());
    }

    /** 메시지 끝의 {@code " id=123"}·{@code " fileName=..."} 같은 내부 식별자를 잘라낸다. */
    private static final java.util.regex.Pattern TRAILING_INTERNAL_ID =
            java.util.regex.Pattern.compile("\\s+(?:[a-z]+)?[iI]d=\\S+$");
    private static final java.util.regex.Pattern TRAILING_IDENTIFIER = java.util.regex.Pattern.compile("\\s+\\w+=\\S+$");

    /** 작성자·관리자가 아닌 사용자의 수정·삭제 시도(403). 다른 오류와 같은 JSON 형식으로 맞춘다. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        // 내부 id·파일명은 로그로만 남긴다.
        log.warn(e.getMessage());
        String message = e.getMessage() == null ? "권한이 없습니다."
                : TRAILING_IDENTIFIER.matcher(e.getMessage()).replaceFirst("");
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, message);
    }

    /**
     * 기준 버전({@code If-Match})이 지금 버전과 다름(412, {@code code: EDIT_CONFLICT}). 저장 시점에 겹친
     * 경우는 {@link #handleEditConflict}의 409이며, 이 타입이 더 구체적이라 먼저 선택된다.
     */
    @ExceptionHandler(PreconditionFailedException.class)
    public ProblemDetail handlePreconditionFailed(PreconditionFailedException e) {
        String className = e.getPersistentClassName();
        String subject = className != null && className.endsWith(".Comment") ? "댓글" : "글";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED,
                "다른 곳에서 이미 수정된 " + subject + "입니다. 새로고침 후 다시 시도해 주세요.");
        problem.setProperty("code", "EDIT_CONFLICT");
        return problem;
    }

    /** 수정 요청에 기준 버전이 없음(428). 조건 없는 덮어쓰기를 막는다. */
    @ExceptionHandler(PreconditionRequiredException.class)
    public ProblemDetail handlePreconditionRequired(PreconditionRequiredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_REQUIRED, e.getMessage());
        problem.setProperty("code", "VERSION_REQUIRED");
        return problem;
    }

    /** 저장 시점의 낙관적 잠금 충돌(409). 충돌한 엔티티 이름으로 "글"/"댓글"을 가린다. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleEditConflict(OptimisticLockingFailureException e) {
        String className = e instanceof ObjectOptimisticLockingFailureException oe
                ? oe.getPersistentClassName() : null;
        String subject = className != null && className.endsWith(".Comment") ? "댓글" : "글";
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "다른 곳에서 이미 수정된 " + subject + "입니다. 새로고침 후 다시 시도해 주세요.");
    }

    /**
     * 제약 위반을 종류별로 나눈다.
     * <ul>
     * <li>UNIQUE — 사전 중복 검사와 INSERT 사이 경쟁. 409.</li>
     * <li>FOREIGN_KEY — 참조 대상이 그사이 삭제·변경됨. 409, 새로고침 안내.</li>
     * <li>NOT_NULL·CHECK — 서버 코드 결함. 5xx 경보에 잡히게 500.</li>
     * <li>그 밖(원인 불명 포함) — 409.</li>
     * </ul>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException e) {
        ConstraintViolationException.ConstraintKind kind = e.getCause() instanceof ConstraintViolationException cve
                ? cve.getKind() : null;
        if (kind == ConstraintViolationException.ConstraintKind.NOT_NULL
                || kind == ConstraintViolationException.ConstraintKind.CHECK) {
            log.error("서버 코드가 제약을 지키지 못했습니다. kind={}", kind, e);
            return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
        }
        log.warn("데이터 무결성 제약을 위반했습니다. kind={}", kind, e);
        if (kind == ConstraintViolationException.ConstraintKind.FOREIGN_KEY) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "대상이 그사이 삭제되었거나 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "이미 사용 중인 값입니다. 다른 값으로 다시 시도해 주세요.");
    }

    /**
     * multipart 수신 한도(6MB) 초과(413). 서비스 검사(5MB)와 같은 문구로 안내한다.
     * <p>
     * 413 본문이 온전히 나가려면 {@code spring.servlet.multipart.resolve-lazily: true}가 필요하다 —
     * 없으면 핸들러 진입 전에 파싱하다 연결이 끊겨 클라이언트는 빈 413만 받는다.
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "파일 크기는 5MB를 초과할 수 없습니다."));
    }

    /** 최종 방어선. 원인은 숨기고 스택 트레이스만 로그에 남긴다. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외가 발생했습니다.", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }
}
