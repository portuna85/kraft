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
        Optional<WinningNumberResponse> latestRound = winningNumberQueryService.findLatest();
        RoundFreshnessResponse freshness = latestRound.isPresent() ? winningNumberQueryService.getFreshness() : null;

        List<HomeCommunityPostSummary> latestPosts = communityPostRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, LATEST_POSTS_LIMIT)).stream()
                .map(HomeSummaryService::toSummary)
                .toList();

        OffsetDateTime since = OffsetDateTime.now(clock).minusDays(WEEKLY_WINDOW_DAYS);
        List<HomeCommunityPostSummary> weeklyPopularPosts = communityPostRepository
                .findByCreatedAtAfterOrderByCreatedAtDesc(since, PageRequest.of(0, WEEKLY_POPULAR_POSTS_LIMIT)).stream()
                .map(HomeSummaryService::toSummary)
                .toList();

        return new HomeResponse(latestRound.orElse(null), freshness, latestPosts, weeklyPopularPosts);
    }

    private static HomeCommunityPostSummary toSummary(CommunityPost post) {
        return new HomeCommunityPostSummary(post.getId(), post.getTitle(), post.getAuthorNameSnapshot(), post.getCreatedAt());
    }
}
