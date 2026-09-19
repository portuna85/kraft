package com.kraft.recommend.web;

import com.kraft.recommend.dto.RecommendRequestDto;
import com.kraft.recommend.dto.RecommendResponseDto;
import com.kraft.recommend.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개·비저장 번호 추천 생성 API(02문서 4절). 로그인 여부와 무관하게 동일하게 동작하고,
 * {@code SecurityConfig}에서 이 경로 하나만 {@code permitAll}이다.
 * <p>
 * {@code app.recommend.enabled=false}면 이 컨트롤러 빈이 등록되지 않아 이 경로는 404가 된다.
 */
@RequiredArgsConstructor
@RestController
@ConditionalOnProperty(prefix = "app.recommend", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RecommendationApiController {

    private final RecommendationService recommendationService;
    private final RecommendationRateLimiter rateLimiter;

    @PostMapping("/api/v1/numbers/recommend")
    public ResponseEntity<?> recommend(@Valid @RequestBody(required = false) RecommendRequestDto request,
                                        HttpServletRequest httpRequest) {
        // 본문이 없거나 빈 객체({}))면 모든 필드가 null인 요청과 동일하게 취급한다(02문서 4절).
        RecommendRequestDto normalizedRequest = request == null
                ? new RecommendRequestDto(null, null, null, null)
                : request;

        String clientKey = httpRequest.getRemoteAddr();
        if (!rateLimiter.tryAcquire(clientKey)) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
            problem.setProperty("code", "RECOMMENDATION_RATE_LIMITED");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .body(problem);
        }

        RecommendResponseDto response = recommendationService.recommend(normalizedRequest);
        return ResponseEntity.ok(response);
    }
}
