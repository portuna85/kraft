package com.kraft.home.web;

import com.kraft.home.service.DrawInsights;
import com.kraft.home.service.HomeInsightsService;
import com.kraft.post.dto.PostsListResponseDto;
import com.kraft.post.service.PostService;
import com.kraft.recommend.domain.LottoPrizeTax;
import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.service.LatestDrawService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * 첫 화면. 예전에는 {@code /}가 게시판이었으므로, 게시판 파라미터가 붙은 요청(저장된 검색·정렬·
 * 페이지 링크)은 {@code /community}로 영구 이동시켜 북마크와 검색 색인이 끊기지 않게 한다.
 * <p>
 * 최신 회차는 {@code app.recommend.enabled}가 켜져 있을 때만 조회한다 — 꺼져 있으면 화면·API·
 * 내비게이션 링크가 함께 사라지는 것이 그 기능 플래그의 약속이다. 이력이 비어 있거나 일부 값이
 * 없는 회차(추첨일·보너스·당첨금)는 해당 조각만 조용히 생략한다.
 */
@Controller
public class HomePageController {

    private static final Set<String> LEGACY_BOARD_PARAMS = Set.of("q", "category", "scope", "sort", "page");
    private static final int RECENT_POSTS = 5;

    private final LatestDrawService latestDrawService;
    private final PostService postService;
    private final HomeInsightsService insightsService;
    private final boolean recommendEnabled;

    public HomePageController(LatestDrawService latestDrawService, PostService postService,
                              HomeInsightsService insightsService,
                              @Value("${app.recommend.enabled:true}") boolean recommendEnabled) {
        this.latestDrawService = latestDrawService;
        this.postService = postService;
        this.insightsService = insightsService;
        this.recommendEnabled = recommendEnabled;
    }

    @GetMapping("/")
    public Object home(HttpServletRequest request, Model model) {
        if (hasLegacyBoardParam(request)) {
            String query = request.getQueryString();
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, URI.create("/community?" + query).toASCIIString())
                    .build();
        }
        model.addAttribute("pageTitle", "데이터로 살펴보는 로또 6/45");
        model.addAttribute("pageDescription",
                "과거 당첨 데이터를 확인하고 서로 다른 기준의 번호 추천을 비교해 보세요. 추천은 당첨 확률을 높이지 않습니다.");
        model.addAttribute("canonicalPath", "/");
        model.addAttribute("recommendEnabled", recommendEnabled);
        if (recommendEnabled) {
            latestDrawService.latest()
                    .ifPresent(draw -> model.addAttribute("latestDraw", LatestDraw.from(draw)));
            DrawInsights insights = insightsService.current();
            if (!insights.isEmpty()) {
                model.addAttribute("insights", insights);
            }
        }
        List<PostsListResponseDto> recentPosts = postService.findRecent(RECENT_POSTS);
        model.addAttribute("recentPosts", recentPosts);
        return "home";
    }

    private static boolean hasLegacyBoardParam(HttpServletRequest request) {
        String query = request.getQueryString();
        return query != null && !query.isBlank()
                && request.getParameterMap().keySet().stream().anyMatch(LEGACY_BOARD_PARAMS::contains);
    }

    /** 화면이 그대로 쓰는 최신 회차 값. 없는 값은 null이라 템플릿이 그 조각을 건너뛴다. */
    public record LatestDraw(int roundNo, List<Integer> numbers, java.time.LocalDate drawDate, Integer bonusNo,
                             Long firstPrizeAmount, Long takeHomeAmount, Integer winnerCount) {

        static LatestDraw from(WinningDraw draw) {
            Long prize = draw.getFirstPrizeAmount();
            return new LatestDraw(draw.getRoundNo(), draw.numbers(), draw.getDrawDate(), draw.getBonusNo(),
                    prize, prize == null ? null : LottoPrizeTax.afterTax(prize), draw.getFirstPrizeWinnerCount());
        }
    }
}
