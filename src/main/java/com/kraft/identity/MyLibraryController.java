package com.kraft.identity;

import com.kraft.community.auth.CommunityPrincipal;
import com.kraft.recommend.RecommendationSetHistoryService;
import com.kraft.recommend.RecommendationSetSummary;
import com.kraft.saved.SavedNumberResponse;
import com.kraft.saved.SavedNumbersService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 계정으로 귀속된 저장 번호·추천 이력의 "통합 보관함" 읽기 — 익명 기기 토큰(/api/v1/saved,
 * /api/v1/recommendation-sets, STATELESS 체인) 대신 이미 세션 인증이 갖춰진
 * /api/v1/community/** 경로 아래 둔다(설계 판단: 기존 익명 흐름의 보안 체인은 바꾸지 않는다).
 */
@RestController
@RequestMapping("/api/v1/community/me")
public class MyLibraryController {

    private final SavedNumbersService savedNumbersService;
    private final RecommendationSetHistoryService recommendationSetHistoryService;

    public MyLibraryController(SavedNumbersService savedNumbersService,
                                RecommendationSetHistoryService recommendationSetHistoryService) {
        this.savedNumbersService = savedNumbersService;
        this.recommendationSetHistoryService = recommendationSetHistoryService;
    }

    @GetMapping("/saved-numbers")
    public ResponseEntity<List<SavedNumberResponse>> savedNumbers(
            @AuthenticationPrincipal CommunityPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(savedNumbersService.listForOwner(principal.getUserId()));
    }

    @GetMapping("/recommendation-sets")
    public ResponseEntity<List<RecommendationSetSummary>> recommendationSets(
            @AuthenticationPrincipal CommunityPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(recommendationSetHistoryService.listForOwner(principal.getUserId()));
    }
}
