package com.kraft.recommend.web;

import com.kraft.recommend.domain.RecommendationFetchAttempt;
import com.kraft.recommend.domain.RecommendationFetchAttemptRepository;
import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.domain.WinningDrawRepository;
import com.kraft.recommend.service.RecommendationAutoFetchScheduler;
import com.kraft.recommend.service.RecommendationFetchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 관리자용 추천 이력 자동 수집 상태 화면. 경로가 {@code /admin} 아래라 {@code SecurityConfig}가
 * 관리자만 들여보낸다 — 이 컨트롤러는 권한을 다시 판정하지 않는다.
 * <p>
 * 마지막 성공·실패와 연속 실패 횟수는 {@link RecommendationFetchStatus}가 메모리에 들고 있지만,
 * 재시작하면 DB의 시도 기록(recommendation_fetch_attempts)으로 되돌려진다. 아래 "최근 수집 이력"은 그
 * 기록을 그대로 보여준다. 지금 수집은 {@link AdminRecommendationFetchApiController}가 맡는다.
 */
@RequiredArgsConstructor
@Controller
@ConditionalOnProperty(prefix = "app.recommend", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdminRecommendationFetchPageController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int HISTORY_SIZE = 20;

    private final RecommendationFetchStatus fetchStatus;
    private final RecommendationHistoryStateRepository stateRepository;
    private final WinningDrawRepository winningDrawRepository;
    private final RecommendationFetchAttemptRepository attemptRepository;

    @Value("${app.recommend.auto-fetch.enabled:true}")
    private boolean autoFetchEnabled;

    @GetMapping("/admin/recommendations")
    public String fetchStatus(Model model) {
        RecommendationHistoryState state = stateRepository.findById(1).orElse(null);
        Integer verifiedThrough = state == null ? null : state.getVerifiedThroughRound();
        WinningDraw latestDraw = winningDrawRepository.findTopByOrderByRoundNoDesc().orElse(null);

        model.addAttribute("autoFetchEnabled", autoFetchEnabled);
        model.addAttribute("verifiedThroughRound", verifiedThrough);
        model.addAttribute("nextRound", (verifiedThrough == null ? 0 : verifiedThrough) + 1);
        model.addAttribute("verifiedAt", state == null ? null : state.getVerifiedAt());
        model.addAttribute("sourceReference", state == null ? null : state.getSourceReference());
        model.addAttribute("latestDrawRound", latestDraw == null ? null : latestDraw.getRoundNo());
        model.addAttribute("latestDrawDate", latestDraw == null ? null : latestDraw.getDrawDate());
        model.addAttribute("nextRunAt", RecommendationAutoFetchScheduler.nextRun(ZonedDateTime.now(KST)).toLocalDateTime());
        model.addAttribute("consecutiveFailures", fetchStatus.consecutiveFailures());
        model.addAttribute("lastSuccessAt", toKst(fetchStatus.lastSuccessAt()));
        model.addAttribute("lastFailureAt", toKst(fetchStatus.lastFailureAt()));
        model.addAttribute("lastFailureReason", fetchStatus.lastFailureReason());
        model.addAttribute("attempts", attemptRepository.findAllByOrderByIdDesc(PageRequest.of(0, HISTORY_SIZE)));
        model.addAttribute("pageTitle", "추천 이력 수집");
        return "admin/recommendations";
    }

    private static LocalDateTime toKst(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, KST);
    }
}
