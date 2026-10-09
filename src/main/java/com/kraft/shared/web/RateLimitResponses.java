package com.kraft.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * 속도 제한 초과 응답을 한 모양으로 만드는 순수 정적 유틸리티.
 * {@code RecommendationApiController}가 먼저 쓰던 모양(ProblemDetail + {@code code} 확장
 * 속성 + {@code Retry-After})을 그대로 따른다 — 여러 컨트롤러(게시글·댓글·신고·업로드)가
 * 같은 모양의 429를 반복해 만들지 않도록 한 곳에 모은다.
 */
public final class RateLimitResponses {

    private RateLimitResponses() {
    }

    /**
     * @param code        {@code ProblemDetail}의 {@code code} 확장 속성. 화면·클라이언트가
     *                    어떤 제한에 걸렸는지 구분할 때 쓴다.
     * @param retryAfterSeconds {@code Retry-After} 헤더 값(초).
     */
    public static ResponseEntity<ProblemDetail> tooManyRequests(String code, long retryAfterSeconds) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        problem.setProperty("code", code);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .body(problem);
    }
}
