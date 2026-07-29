package com.kraft.home;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인화 필드가 없는 공개 홈 요약 API. 캐시 헤더(짧은 공개 캐시 + ETag)는
 * {@link com.kraft.common.web.PublicApiCacheControlFilter}가 경로 기준으로 일괄 적용한다.
 */
@RestController
@RequestMapping("/api/v1/home")
public class HomeApiController {

    private final HomeSummaryService homeSummaryService;

    public HomeApiController(HomeSummaryService homeSummaryService) {
        this.homeSummaryService = homeSummaryService;
    }

    @GetMapping
    public HomeResponse home() {
        return homeSummaryService.summarize();
    }
}
