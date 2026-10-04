package com.kraft.recommend.web;

import com.kraft.recommend.domain.RecommendationFetchAttempt.Trigger;
import com.kraft.recommend.service.RecommendationFetchService;
import com.kraft.recommend.service.RecommendationFetchService.RunResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 화면의 "지금 수집". {@code SecurityConfig}가 {@code /api/v1/admin/**}를 관리자만 들여보낸다.
 * 예약 실행과 같은 {@link RecommendationFetchService}를 쓰며, 이미 수집이 돌고 있으면 새로 시작하지
 * 않고 그 사실을 알린다(BUSY). 동행복권 응답이 막혀도 HTTP 오류가 아니라 결과 상태로 알린다 —
 * 요청 자체는 정상 처리됐고 화면이 사유를 그대로 보여준다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.recommend", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdminRecommendationFetchApiController {

    private final RecommendationFetchService fetchService;

    /**
     * @param status         DONE / FAILED / CATCHUP_LIMIT / BUSY
     * @param fetchedRounds  이번 실행에서 반영한 회차
     * @param message        화면에 그대로 보여줄 한 줄 요약
     */
    public record FetchResponse(String status, List<Integer> fetchedRounds, String message) {
    }

    @PostMapping("/api/v1/admin/recommendations/fetch")
    public FetchResponse fetchNow(Authentication authentication) {
        log.info("관리자 수동 수집 요청. userId={}", authentication.getName());
        RunResult result = fetchService.run(Trigger.MANUAL);
        return new FetchResponse(result.status().name(), result.fetchedRounds(), result.message());
    }
}
