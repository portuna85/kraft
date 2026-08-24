package com.kraft.common.error;

import org.springframework.http.HttpStatus;

/**
 * M-2: {@link ApiException}이 (HttpStatus, 문자열 code) 쌍을 호출부마다 직접 타이핑하던 것을
 * enum으로 승격했다. 84곳의 호출부에서 상태코드-code 오타/불일치를 컴파일 시점에 방지한다.
 */
public enum ApiErrorCode {

    INVALID_BALL(HttpStatus.BAD_REQUEST),
    INVALID_BONUS_NUMBER(HttpStatus.BAD_REQUEST),
    INVALID_NUMBERS(HttpStatus.BAD_REQUEST),
    INVALID_ROUND(HttpStatus.BAD_REQUEST),
    INVALID_LIMIT(HttpStatus.BAD_REQUEST),
    ROUND_NOT_FOUND(HttpStatus.NOT_FOUND),
    // BE-STAT-01(docs/improvement.md): 통계 summary가 재계산 후에도(또는 다른 인스턴스의
    // 재계산을 기다린 뒤에도) 여전히 불완전할 때만 쓴다 — 완전성이 검증되지 않은 데이터를
    // 200으로 조용히 돌려주지 않기 위한 명시적 신호.
    STATISTICS_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE),

    DEVICE_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST),
    INVALID_DEVICE_TOKEN(HttpStatus.BAD_REQUEST),
    DEVICE_ALREADY_CLAIMED(HttpStatus.CONFLICT),

    OAUTH_PROVIDER_UNSUPPORTED(HttpStatus.BAD_REQUEST),
    OAUTH_ATTRIBUTE_MISSING(HttpStatus.UNAUTHORIZED),

    SAVED_NUMBER_NOT_FOUND(HttpStatus.NOT_FOUND),
    SAVED_LIMIT_REACHED(HttpStatus.CONFLICT),

    RECOMMENDATION_SET_NOT_FOUND(HttpStatus.NOT_FOUND),
    RECOMMENDATION_SET_NOT_OWNED(HttpStatus.FORBIDDEN),
    RECOMMENDATION_SET_ATTACHED_TO_POST(HttpStatus.CONFLICT),
    // I-04: strategy/explanation_codes 저장값이 현재 enum과 더 이상 맞지 않는 레거시 행을
    // 디코드하려 할 때(예: 리팩터 이전에 생성된 추천 세트) 원시 RuntimeException이 그대로
    // 새어나가 "예상하지 못한 서버 오류가 발생했습니다"라는 정체불명의 500이 되던 문제를 막는다.
    RECOMMENDATION_SET_UNAVAILABLE(HttpStatus.CONFLICT),
    TOO_MANY_LOCKED_NUMBERS(HttpStatus.BAD_REQUEST),
    LOCKED_EXCLUDED_CONFLICT(HttpStatus.BAD_REQUEST),
    INVALID_RECOMMENDATION_STRATEGY(HttpStatus.BAD_REQUEST),
    TOO_MANY_EXCLUSIONS(HttpStatus.BAD_REQUEST),
    INSUFFICIENT_UNIQUE_COMBINATIONS(HttpStatus.BAD_REQUEST),
    RECOMMENDATION_HISTORY_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE),

    COMMUNITY_CATEGORY_INVALID(HttpStatus.BAD_REQUEST),
    // API-COMM-01(docs/improvement.md): 알 수 없는 sort를 조용히 latest로 처리하지 않는다.
    INVALID_SORT(HttpStatus.BAD_REQUEST),
    COMMUNITY_SEARCH_QUERY_INVALID(HttpStatus.BAD_REQUEST),
    COMMUNITY_SELF_BLOCK_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
    COMMUNITY_BLOCKED_INTERACTION(HttpStatus.FORBIDDEN),
    COMMUNITY_POST_NOT_FOUND(HttpStatus.NOT_FOUND),
    COMMUNITY_POST_NOT_VISIBLE(HttpStatus.NOT_FOUND),
    COMMUNITY_POST_NOT_OWNER(HttpStatus.FORBIDDEN),
    COMMUNITY_POST_VERSION_CONFLICT(HttpStatus.CONFLICT),
    COMMUNITY_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    COMMUNITY_COMMENT_NOT_OWNER(HttpStatus.FORBIDDEN),
    COMMUNITY_COMMENT_PARENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    COMMUNITY_COMMENT_PARENT_DELETED(HttpStatus.BAD_REQUEST),
    COMMUNITY_COMMENT_PARENT_MISMATCH(HttpStatus.BAD_REQUEST),
    COMMUNITY_COMMENT_REPLY_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST),
    COMMUNITY_USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT),
    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND),

    LOTTO_SOURCE_DISABLED(HttpStatus.SERVICE_UNAVAILABLE),
    LOTTO_SOURCE_CIRCUIT_OPEN(HttpStatus.BAD_GATEWAY),
    LOTTO_SOURCE_EMPTY(HttpStatus.BAD_GATEWAY),
    LOTTO_SOURCE_INVALID_DATE(HttpStatus.BAD_GATEWAY),
    LOTTO_SOURCE_PARSE_ERROR(HttpStatus.BAD_GATEWAY),
    LOTTO_SOURCE_ROUND_MISMATCH(HttpStatus.BAD_GATEWAY),
    LOTTO_SOURCE_ROUND_NOT_FOUND(HttpStatus.BAD_GATEWAY),
    LOTTO_SOURCE_VALIDATION_ERROR(HttpStatus.BAD_GATEWAY),

    INVALID_OPERATION_TYPE(HttpStatus.BAD_REQUEST),
    INVALID_EXECUTION_STATUS(HttpStatus.BAD_REQUEST),
    INVALID_FROM_DATE(HttpStatus.BAD_REQUEST),
    INVALID_TO_DATE(HttpStatus.BAD_REQUEST),

    // OBS-WEB-01(docs/improvement.md): web 컨테이너가 내부망 경계 없이 보내는 관측
    // 이벤트(vitals/CSP/client error) 수신 endpoint의 공유 시크릿 인증 실패.
    OBSERVABILITY_UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    // BE-API-01(docs/improvement.md): GlobalExceptionHandler의 프레임워크 예외 핸들러와
    // ApiErrorResponseWriter.write(...)를 직접 부르는 필터/보안 설정이 문자열 리터럴로 쓰던
    // code를 여기로 편입했다 — write(...)/errorBody(...)의 code 파라미터를 ApiErrorCode로
    // 좁혀서 이 enum 밖의 리터럴을 컴파일러가 막게 한다.
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MISSING_HEADER(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER_TYPE(HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    COMMUNITY_WRITE_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    COMMUNITY_ACCOUNT_DELETED(HttpStatus.UNAUTHORIZED),
    COMMUNITY_CSRF_REJECTED(HttpStatus.FORBIDDEN),
    COMMUNITY_ACCESS_DENIED(HttpStatus.FORBIDDEN),
    COMMUNITY_LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED),
    OPS_DISABLED(HttpStatus.SERVICE_UNAVAILABLE),
    OPS_UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
