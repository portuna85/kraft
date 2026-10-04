package com.kraft.recommend.web;

import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.service.RecommendationFetchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 관리자용 추천 이력 자동 수집 상태 화면. 경로가 {@code /admin} 아래라 {@code SecurityConfig}가
 * 관리자만 들여보낸다 — 이 컨트롤러는 권한을 다시 판정하지 않는다.
 * <p>
 * 마지막 성공·실패 시각과 연속 실패 횟수는 {@link RecommendationFetchStatus}가 메모리에만
 * 들고 있어 재시작하면 비어 있다 — 화면은 이를 "기록 없음"으로 보여주고, 재시작과 무관한
 * 진행 지표로는 DB의 검증 기준 회차({@code verifiedThroughRound})를 함께 보여준다.
 */
@RequiredArgsConstructor
@Controller
@ConditionalOnProperty(prefix = "app.recommend", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdminRecommendationFetchPageController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RecommendationFetchStatus fetchStatus;
    private final RecommendationHistoryStateRepository stateRepository;

    @Value("${app.recommend.auto-fetch.enabled:true}")
    private boolean autoFetchEnabled;

    @GetMapping("/admin/recommendations")
    public String fetchStatus(Model model) {
        RecommendationHistoryState state = stateRepository.findById(1).orElse(null);
        Integer verifiedThrough = state == null ? null : state.getVerifiedThroughRound();

        model.addAttribute("autoFetchEnabled", autoFetchEnabled);
        model.addAttribute("verifiedThroughRound", verifiedThrough);
        model.addAttribute("nextRound", (verifiedThrough == null ? 0 : verifiedThrough) + 1);
        model.addAttribute("verifiedAt", state == null ? null : state.getVerifiedAt());
        model.addAttribute("sourceReference", state == null ? null : state.getSourceReference());
        model.addAttribute("consecutiveFailures", fetchStatus.consecutiveFailures());
        model.addAttribute("lastSuccessAt", toKst(fetchStatus.lastSuccessAt()));
        model.addAttribute("lastFailureAt", toKst(fetchStatus.lastFailureAt()));
        model.addAttribute("lastFailureReason", fetchStatus.lastFailureReason());
        model.addAttribute("pageTitle", "추천 이력 수집");
        return "admin/recommendations";
    }

    private static LocalDateTime toKst(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, KST);
    }
}
