package com.kraft.home;

import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.winningnumber.RoundFreshnessResponse;
import com.kraft.winningnumber.WinningNumberQueryService;
import com.kraft.winningnumber.WinningNumberResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HomeSummaryService {

    private static final int LATEST_POSTS_LIMIT = 5;
    private static final int WEEKLY_POPULAR_POSTS_LIMIT = 5;
    private static final int WEEKLY_WINDOW_DAYS = 7;

    private final WinningNumberQueryService winningNumberQueryService;
    private final CommunityPostRepository communityPostRepository;
    private final Clock clock;

    public HomeSummaryService(WinningNumberQueryService winningNumberQueryService,
                              CommunityPostRepository communityPostRepository,
                              Clock clock) {
        this.winningNumberQueryService = winningNumberQueryService;
        this.communityPostRepository = communityPostRepository;
        this.clock = clock;
    }

    public HomeResponse summarize() {
        // BE-PERF-01: findLatest()는 캐시된다(BE-CACHE-01) — getFreshness()를 따로 부르면 같은
        // 회차를 다시 조회하는 자기호출 함정에 걸리므로, 이미 가진 결과를 freshnessOf()에 넘겨
        // 쿼리 없이 조합한다.
        Optional<WinningNumberResponse> latestRound = winningNumberQueryService.findLatest();
        RoundFreshnessResponse freshness = latestRound.map(winningNumberQueryService::freshnessOf).orElse(null);

        // B-P0-1: 홈은 category/query 필터가 없는 화면이므로 null을 넘겨 PUBLISHED 전체를 대상으로
        // 한다. findLatestPublished/findWeeklyPopularPublished는 둘 다 status = PUBLISHED 조건이
        // 있어 숨김·삭제 게시글이 노출되지 않는다(과거에는 이 필터가 없는
        // findAllByOrderByCreatedAtDesc / findByCreatedAtAfterOrderByCreatedAtDesc를 써서 숨김
        // 글이 그대로 노출되는 결함이 있었다). List 반환 전용 메서드를 쓰는 이유는 이 화면이
        // getTotalElements()를 쓰지 않는데 Page<> 반환 메서드는 그 때문에 불필요한 count 쿼리를
        // 함께 실행하기 때문이다(BE-PERF-01) — 실제 페이지네이션이 필요한
        // CommunityPostService.list()는 계속 Page<> 반환 메서드를 쓴다.
        List<HomeCommunityPostSummary> latestPosts = communityPostRepository
                .findLatestPublished(null, null, PageRequest.of(0, LATEST_POSTS_LIMIT)).stream()
                .map(HomeSummaryService::toSummary)
                .toList();

        OffsetDateTime since = OffsetDateTime.now(clock).minusDays(WEEKLY_WINDOW_DAYS);
        List<HomeCommunityPostSummary> weeklyPopularPosts = communityPostRepository
                .findWeeklyPopularPublished(null, null, since, PageRequest.of(0, WEEKLY_POPULAR_POSTS_LIMIT)).stream()
                .map(HomeSummaryService::toSummary)
                .toList();

        return new HomeResponse(latestRound.orElse(null), freshness, latestPosts, weeklyPopularPosts);
    }

    private static HomeCommunityPostSummary toSummary(CommunityPost post) {
        return new HomeCommunityPostSummary(post.getId(), post.getTitle(), post.getAuthorNameSnapshot(), post.getCreatedAt());
    }
}
