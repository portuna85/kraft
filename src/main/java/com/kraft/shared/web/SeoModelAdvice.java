package com.kraft.shared.web;

import com.kraft.post.web.PostPageController;
import com.kraft.recommend.web.RecommendationPageController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 공개 화면의 canonical·og:url에 쓸 대표 주소(평가 보고서 2026-09-25 F08).
 * <p>
 * 요청의 Host 헤더가 아니라 설정값({@code app.base-url}, 운영은 {@code APP_BASE_URL})을 쓴다.
 * Host는 클라이언트가 보낸 값이라, 그대로 쓰면 임의의 도메인을 대표 주소로 선언하는 페이지를
 * 만들 수 있다. www와 apex가 모두 200을 돌려주는 지금도 대표 주소는 이 설정 하나로 정해진다.
 * 색인 대상인 화면 컨트롤러에만 적용한다 — 로그인·관리자 등은 X-Robots-Tag로 색인에서 뺀다
 * ({@code SecurityConfig#isNoindexPath}).
 */
@ControllerAdvice(assignableTypes = { PostPageController.class, RecommendationPageController.class })
public class SeoModelAdvice {

    private final String siteBaseUrl;

    public SeoModelAdvice(@Value("${app.base-url}") String baseUrl) {
        this.siteBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @ModelAttribute("siteBaseUrl")
    public String siteBaseUrl() {
        return siteBaseUrl;
    }
}
