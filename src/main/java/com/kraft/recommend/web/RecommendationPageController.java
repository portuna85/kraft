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
 * 번호 추천 화면. 폼은 Vue 아일랜드(src/vue/recommend)가 그리고, 생성은 사용자가 버튼을 눌렀을
 * 때만 REST API({@code POST /api/v1/numbers/recommend})로 요청한다. 서버가 미리 계산해
 * 내려줄 초기 상태가 없으므로(signup 화면과 동일한 이유) 부트스트랩 JSON은 두지 않는다.
 * <p>
 * 다만 최신 회차 당첨번호는 페이지 방문마다 바뀌지 않는(다음 추첨 전까지 고정) 정적인 정보라
 * 서버 렌더링으로 바로 내려준다 — 굳이 Vue·API 왕복을 거칠 이유가 없다. 이력이 비어 있으면
 * (아직 한 회차도 반영되지 않았으면) 조용히 생략한다.
 * <p>
 * {@code app.recommend.enabled=false}면 이 컨트롤러 빈 자체가 등록되지 않아 {@code /recommend}는
 * 자연스럽게 404가 된다(운영 준비 — 기능을 끄면 화면·API·내비게이션 링크가 일관되게 사라져야
 * 한다).
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
        // 화면의 안내 문구와 같은 성격으로 쓴다 — 당첨 확률을 높인다고 읽히면 안 된다(F08).
        model.addAttribute("pageDescription",
                "역대 1등 당첨 조합을 뺀 로또 번호 조합을 추천받고 최근 회차 당첨 번호를 확인합니다. 당첨 확률을 높이는 기능은 아닙니다.");
        model.addAttribute("canonicalPath", "/recommend");
        RecommendationFreshness.Status history = freshness.current();
        model.addAttribute("historyReady", history.ready());
        model.addAttribute("historyStale", history.stale());
        model.addAttribute("historyVerifiedRound", history.verifiedThroughRound());
        model.addAttribute("historyVerifiedAt", history.verifiedAt());
        // 홈과 같은 조각(templates/lotto/latest-draw.html)이 읽는 값이다. 없는 값은 null이라 그 조각만 생략된다.
        latestDrawService.latest().ifPresent(draw -> model.addAttribute("latestDraw", LatestDrawView.from(draw)));
        return "recommend/recommend";
    }
}
