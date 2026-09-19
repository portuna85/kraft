package com.kraft.recommend.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 번호 추천 화면. 폼은 Vue 아일랜드(src/vue/recommend)가 그리고, 생성은 사용자가 버튼을 눌렀을
 * 때만 REST API({@code POST /api/v1/numbers/recommend})로 요청한다. 서버가 미리 계산해
 * 내려줄 초기 상태가 없으므로(signup 화면과 동일한 이유) 부트스트랩 JSON은 두지 않는다.
 * <p>
 * {@code app.recommend.enabled=false}면 이 컨트롤러 빈 자체가 등록되지 않아 {@code /recommend}는
 * 자연스럽게 404가 된다(운영 준비 — 기능을 끄면 화면·API·내비게이션 링크가 일관되게 사라져야
 * 한다).
 */
@Controller
@ConditionalOnProperty(prefix = "app.recommend", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RecommendationPageController {

    @GetMapping("/recommend")
    public String recommend(Model model) {
        model.addAttribute("pageTitle", "번호 추천");
        return "recommend/recommend";
    }
}
