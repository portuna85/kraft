package com.kraft.recommend.web;

import com.kraft.recommend.dto.LatestDrawView;
import com.kraft.recommend.service.LatestDrawService;
import com.kraft.recommend.service.RecommendationFreshness;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 번호 추천 화면. 폼은 Vue 아일랜드(src/vue/recommend)가 그리고 생성은 버튼을 눌렀을 때만 REST API로 요청하므로 부트스트랩 JSON이
 * 없다. 최신 회차 당첨번호는 다음 추첨 전까지 고정이라 서버 렌더링으로 내려준다(이력이 비면 생략). {@code app.recommend.enabled=false}면
 * 빈이 등록되지 않아 {@code /recommend}가 404가 된다 — 화면·API·내비게이션 링크가 함께 사라진다.
 */
@Controller
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.recommend", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RecommendationPageController {

    private final LatestDrawService latestDrawService;
    private final RecommendationFreshness freshness;

    @GetMapping("/recommend")
    public String recommend(Model model) {
        model.addAttribute("pageTitle", "번호 추천");
        // 당첨 확률을 높인다고 읽히면 안 된다.
        model.addAttribute("pageDescription",
                "역대 1등 당첨 조합을 뺀 로또 번호 조합을 추천받고 최근 회차 당첨 번호를 확인합니다. 당첨 확률을 높이는 기능은 아닙니다.");
        model.addAttribute("canonicalPath", "/recommend");
        RecommendationFreshness.Status history = freshness.current();
        model.addAttribute("historyReady", history.ready());
        model.addAttribute("historyStale", history.stale());
        model.addAttribute("historyVerifiedRound", history.verifiedThroughRound());
        model.addAttribute("historyVerifiedAt", history.verifiedAt());
        // templates/lotto/latest-draw.html 조각이 읽는 값(없으면 그 조각만 생략된다).
        latestDrawService.latest().ifPresent(draw -> model.addAttribute("latestDraw", LatestDrawView.from(draw)));
        return "recommend/recommend";
    }
}
