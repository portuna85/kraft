package com.kraft.shared.web;

import com.kraft.post.domain.PostNotFoundException;
import com.kraft.recommend.domain.RecommendationGenerationLimitException;
import com.kraft.recommend.domain.RecommendationHistoryNotReadyException;
import com.kraft.recommend.domain.RecommendationValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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

import java.util.stream.Collectors;

/**
 * 기능별 패키지의 {@link RestController} 전용 전역 예외 처리기.
 * <p>
 * 화면 컨트롤러는 이 범위에 포함하지 않는다. 브라우저가 렌더링하는
 * Thymeleaf 뷰 요청에 JSON 오류 응답을 내려주는 것은 적절하지 않기 때문이다.
 * <p>
 * Spring Boot 4가 지원하는 {@link ProblemDetail}(RFC 9457)을 그대로 반환한다 — 컨트롤러/
 * {@code @ExceptionHandler} 메서드가 {@code ProblemDetail}을 반환하면 프레임워크가 상태 코드와
 * {@code Content-Type: application/problem+json}을 자동으로 설정하므로 별도 래퍼 클래스나
 * 신규 의존성이 필요 없다.
 * <p>
 * {@link ResponseEntityExceptionHandler}를 상속한다(개선 보고서 OBS-04) — 상속 전에는 전용
 * 핸들러가 없는 표준 MVC 예외(필수 파라미터·멀티파트 파트 누락, 지원하지 않는 HTTP 메서드·
 * 미디어 타입 등)가 죄다 {@link #handleUnexpected}(500)에 걸렸다. 클라이언트 잘못으로 인한
 * 4xx 요청이 서버 오류로 집계되고 5xx 알림까지 울렸다. 이 부모 클래스는 그런 예외들을 이미
 * 제 상태 코드의 {@code ProblemDetail}로 변환해 주므로, 아래에서 더 구체적인 메시지가 필요한
 * {@link MethodArgumentNotValidException}·{@link HttpMessageNotReadableException}만 오버라이드
 * 없이 이 클래스 자신의 {@code @ExceptionHandler}로 남겨 둔다 — 같은 타입에 더 가까운(subclass)
 * 선언이 있으면 그쪽이 부모의 처리보다 우선 선택된다.
 */
@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

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
     * "대상이 없음"은 검증 실패가 아니라 404다. {@link PostNotFoundException}이
     * {@link IllegalArgumentException}을 상속하는 탓에 API에서도 400으로 나갔는데, 클라이언트가
     * "요청이 잘못됨"과 "글이 삭제됨"을 구분할 수 없었다. 더 구체적인 타입의 핸들러가 우선하므로
     * 이 메서드가 위의 {@link #handleIllegalArgument}보다 먼저 선택된다.
     */
    @ExceptionHandler(PostNotFoundException.class)
    public ProblemDetail handleNotFound(PostNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * 번호 추천 요청 검증·실현 가능성 실패(400). {@link RecommendationValidationException}이
     * {@link IllegalArgumentException}을 상속하는 탓에 {@link #handleIllegalArgument}도 잡을 수
     * 있지만, 더 구체적인 타입의 핸들러가 우선한다 — {@code code} 확장 속성을 추가하기 위한
     * 전용 핸들러다(이 코드베이스에서 {@code ProblemDetail}에 {@code code}를 붙이는 첫 사례).
     */
    @ExceptionHandler(RecommendationValidationException.class)
    public ProblemDetail handleRecommendationValidation(RecommendationValidationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("code", e.getCode());
        return problem;
    }

    /**
     * 검증된 당첨 이력이 준비되지 않았거나(비어 있음·누락·미검증) 생성 도중 버전이 바뀐
     * 경우(503, HIST-03/HIST-04). 이력 미준비는 추천 API만 실패시키고 게시판 전체 기동에는
     * 영향을 주지 않는다.
     */
    @ExceptionHandler(RecommendationHistoryNotReadyException.class)
    public ProblemDetail handleRecommendationHistoryNotReady(RecommendationHistoryNotReadyException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setProperty("code", RecommendationHistoryNotReadyException.CODE);
        return problem;
    }

    /**
     * 수학적으로는 가능하지만 반복 상한 안에 요청 개수를 채우지 못한 경우(503). 수학적으로
     * 불가능한 경우({@code INSUFFICIENT_UNIQUE_COMBINATIONS})와 원인을 구분한다(03문서 6절).
     */
    @ExceptionHandler(RecommendationGenerationLimitException.class)
    public ProblemDetail handleRecommendationGenerationLimit(RecommendationGenerationLimitException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setProperty("code", RecommendationGenerationLimitException.CODE);
        return problem;
    }

    /**
     * {@code @Valid @RequestBody} 검증 실패(예: 빈 제목)를 400으로 변환하고,
     * 필드별 오류 메시지를 하나의 문자열로 모아 반환한다.
     * <p>
     * {@code @ExceptionHandler}로 새로 선언하지 않고 부모의 같은 메서드를 오버라이드한다 —
     * {@link ResponseEntityExceptionHandler}도 이 타입을 자신의 {@code handleException}
     * 매핑에 이미 포함하고 있어서, 별도의 {@code @ExceptionHandler(MethodArgumentNotValidException.class)}
     * 메서드를 하나 더 선언하면 한 예외 타입에 서로 다른 두 메서드가 매핑되어 스프링이
     * "Ambiguous @ExceptionHandler"로 기동에 실패한다. 오버라이드는 이 한 슬롯을 그대로
     * 대체하므로 충돌이 없다.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message));
    }

    /**
     * 요청 본문이 JSON으로 파싱되지 않는 경우(형식 오류, 잘못된 인코딩 등) 400으로 변환한다.
     * 위와 같은 이유로 새 {@code @ExceptionHandler}가 아니라 부모 메서드를 오버라이드한다.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다."));
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
     * 편집 충돌을 409로 변환한다. 화면이 편집을 시작할 때 받아간 게시글 버전과 저장 시점의
     * DB 버전이 다르면(그 사이 다른 곳에서 저장됨) {@code PostService.update()}가 이 예외를
     * 던진다. 예전에는 나중 저장이 먼저 저장을 말없이 덮어썼다.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleEditConflict(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "다른 곳에서 이미 수정된 글입니다. 새로고침 후 다시 시도해 주세요.");
    }

    /**
     * 유니크 제약 위반(예: 같은 이름으로 동시에 가입)을 409로 변환한다. 서비스의 사전 중복
     * 검사와 INSERT 사이의 경쟁은 DB 제약만이 최종적으로 막을 수 있고, 그때 나오는 예외가
     * catch-all에 잡혀 500이 되지 않게 한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 제약을 위반했습니다.", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "이미 사용 중인 값입니다. 다른 값으로 다시 시도해 주세요.");
    }

    /**
     * multipart 요청이 {@code application.yml}의 수신 한도({@code spring.servlet.multipart.
     * max-file-size/max-request-size})를 넘었을 때 413으로 변환한다. 이 한도는 서비스 검사(5MB)
     * 보다 한 단계 위(6MB)로 잡아두었으므로, 여기 도달한다는 것은 서비스의 "5MB 초과" 안내로도
     * 부족할 만큼 큰 요청이라는 뜻이다 — 같은 사용자 문구로 안내해 두 경로의 오류가 다르게
     * 보이지 않게 한다.
     * <p>
     * 실측(로컬 bootRun + curl, 6MB 초과 파일 업로드)으로 두 가지를 확인했다:
     * <ol>
     * <li>{@code spring.servlet.multipart.resolve-lazily: true}(application.yml)가 없으면
     * DispatcherServlet이 핸들러 진입 "전" checkMultipart() 단계에서 멀티파트 전체를 즉시
     * 파싱한다. 한도 초과가 스트림을 읽는 도중에 발생해 Tomcat이 완료되지 못한 요청 바디를
     * 그대로 끊어버리므로, 이 핸들러가 호출되기는 하지만 커넥션이 먼저 리셋되어(TCP RST)
     * 클라이언트는 본문 없는 413(Content-Length: 0, Connection: close)만 받는다.</li>
     * <li>{@code resolve-lazily: true}로 파싱을 컨트롤러가 실제로 파라미터에 접근하는 시점까지
     * 미루면, 예외가 정상적인 요청 처리 흐름 안에서 발생해 이 핸들러가 413 JSON 본문을
     * 온전히 내려줄 수 있다 — 재검증으로 확인 완료.</li>
     * </ol>
     * {@link ResponseEntityExceptionHandler}를 상속한 뒤부터(OBS-04)는 이 타입도 부모의
     * {@code handleException} 매핑에 이미 포함되어 있어, 새 {@code @ExceptionHandler}
     * 메서드 대신 부모의 같은 메서드를 오버라이드한다 — 그렇지 않으면 기동 시점에
     * "Ambiguous @ExceptionHandler"로 실패한다({@link #handleMethodArgumentNotValid}와 같은
     * 이유).
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "파일 크기는 5MB를 초과할 수 없습니다."));
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
