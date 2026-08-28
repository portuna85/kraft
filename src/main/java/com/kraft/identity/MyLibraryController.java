package com.kraft.identity;

import com.kraft.common.web.PageResponse;
import com.kraft.community.auth.CommunityPrincipal;
import com.kraft.recommend.LottoRecommendationService;
import com.kraft.recommend.RecommendNumbersRequest;
import com.kraft.recommend.RecommendNumbersResponse;
import com.kraft.recommend.RecommendationSetHistoryService;
import com.kraft.recommend.RecommendationSetSummary;
import com.kraft.saved.CreateSavedNumberRequest;
import com.kraft.saved.SaveNumberResult;
import com.kraft.saved.SavedNumberMatchResult;
import com.kraft.saved.SavedNumberResponse;
import com.kraft.saved.SavedNumbersService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final LottoRecommendationService lottoRecommendationService;

    public MyLibraryController(SavedNumbersService savedNumbersService,
                                RecommendationSetHistoryService recommendationSetHistoryService,
                                LottoRecommendationService lottoRecommendationService) {
        this.savedNumbersService = savedNumbersService;
        this.recommendationSetHistoryService = recommendationSetHistoryService;
        this.lottoRecommendationService = lottoRecommendationService;
    }

    @GetMapping("/saved-numbers")
    public ResponseEntity<List<SavedNumberResponse>> savedNumbers(
            @AuthenticationPrincipal CommunityPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(savedNumbersService.listForOwner(principal.getUserId()));
    }

    /**
     * B-P0-3: {@code /api/v1/saved}는 기기 토큰(STATELESS) 전용이라 claim 이후에는 그 저장
     * 번호를 더 이상 찾지 못한다(client_token_hash가 null로 지워지기 때문). 로그인 세션으로
     * 저장·회차 대조·삭제가 가능한 경로를 별도로 둔다.
     */
    @GetMapping("/saved-numbers/matches")
    public ResponseEntity<List<SavedNumberMatchResult>> savedNumberMatches(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @RequestParam(name = "round", defaultValue = "latest") String round) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(savedNumbersService.compareWithRoundForOwner(principal.getUserId(), round));
    }

    @PostMapping("/saved-numbers")
    // 이미 저장된 조합이면 200, 새로 저장됐으면 201 — result.created()에 따라 갈린다(중복 저장 안내 vs 신규 생성).
    @ApiResponses({@ApiResponse(responseCode = "200"), @ApiResponse(responseCode = "201")})
    public ResponseEntity<SaveNumberResult> saveSavedNumber(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @Valid @RequestBody CreateSavedNumberRequest request) {
        SaveNumberResult result = savedNumbersService.saveForOwner(principal.getUserId(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }

    @DeleteMapping("/saved-numbers/{id}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteSavedNumber(
            @AuthenticationPrincipal CommunityPrincipal principal, @PathVariable long id) {
        savedNumbersService.deleteForOwner(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * KF-01(docs/improvement.md): 로그인 계정으로 추천을 생성·영속화한다.
     * {@code /api/v1/numbers/recommend}(익명, device-token STATELESS 체인)와 달리
     * 소유자는 {@link CommunityPrincipal}(이미 인증된 세션)에서만 온다 — 클라이언트가
     * 보낸 계정 식별자를 신뢰하지 않는다.
     */
    @PostMapping("/recommendation-sets")
    public RecommendNumbersResponse recommend(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @Valid @RequestBody(required = false) RecommendNumbersRequest request) {
        return lottoRecommendationService.recommendForOwner(request, principal.getUserId());
    }

    @GetMapping("/recommendation-sets")
    public ResponseEntity<PageResponse<RecommendationSetSummary>> recommendationSets(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<RecommendationSetSummary> result =
                recommendationSetHistoryService.listForOwner(principal.getUserId(), page, size);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(result));
    }

}
