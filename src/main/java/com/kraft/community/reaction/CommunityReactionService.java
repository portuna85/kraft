package com.kraft.community.reaction;

import com.kraft.community.block.CommunityBlockService;
import com.kraft.community.post.CommunityPostMetricsRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommunityReactionService {

    private final CommunityPostLikeRepository communityPostLikeRepository;
    private final CommunityPostBookmarkRepository communityPostBookmarkRepository;
    private final CommunityPostMetricsRepository communityPostMetricsRepository;
    private final CommunityBlockService communityBlockService;
    private final Clock clock;
    private final Counter likeCreatedCounter;
    private final Counter bookmarkCreatedCounter;

    public CommunityReactionService(CommunityPostLikeRepository communityPostLikeRepository,
                                     CommunityPostBookmarkRepository communityPostBookmarkRepository,
                                     CommunityPostMetricsRepository communityPostMetricsRepository,
                                     CommunityBlockService communityBlockService,
                                     Clock clock,
                                     MeterRegistry meterRegistry) {
        this.communityPostLikeRepository = communityPostLikeRepository;
        this.communityPostBookmarkRepository = communityPostBookmarkRepository;
        this.communityPostMetricsRepository = communityPostMetricsRepository;
        this.communityBlockService = communityBlockService;
        this.clock = clock;
        this.likeCreatedCounter = Counter.builder("kraft_community_reaction_created_total")
                .description("생성된 반응(좋아요/북마크) 수")
                .tag("type", "like")
                .register(meterRegistry);
        this.bookmarkCreatedCounter = Counter.builder("kraft_community_reaction_created_total")
                .description("생성된 반응(좋아요/북마크) 수")
                .tag("type", "bookmark")
                .register(meterRegistry);
    }

    /** 멱등 PUT — 이미 좋아요를 눌렀어도 성공으로 흡수한다(REACTION_ALREADY_APPLIED는 오류가 아니다). */
    public void like(Long postId, Long userId) {
        if (communityPostLikeRepository.findByPostIdAndUserId(postId, userId).isEmpty()) {
            communityPostLikeRepository.save(new CommunityPostLike(postId, userId, OffsetDateTime.now(clock)));
            communityPostMetricsRepository.incrementLikeCount(postId);
            likeCreatedCounter.increment();
        }
    }

    /** 멱등 DELETE — 좋아요가 없었어도 그냥 성공(204)으로 처리한다. */
    public void unlike(Long postId, Long userId) {
        if (communityPostLikeRepository.findByPostIdAndUserId(postId, userId).isPresent()) {
            communityPostLikeRepository.deleteByPostIdAndUserId(postId, userId);
            communityPostMetricsRepository.decrementLikeCount(postId);
        }
    }

    public void bookmark(Long postId, Long userId) {
        if (communityPostBookmarkRepository.findByPostIdAndUserId(postId, userId).isEmpty()) {
            communityPostBookmarkRepository.save(new CommunityPostBookmark(postId, userId, OffsetDateTime.now(clock)));
            bookmarkCreatedCounter.increment();
        }
    }

    public void unbookmark(Long postId, Long userId) {
        communityPostBookmarkRepository.deleteByPostIdAndUserId(postId, userId);
    }

    @Transactional(readOnly = true)
    public CommunityInteractionsResponse interactions(Long userId, List<Long> postIds) {
        Set<Long> liked = communityPostLikeRepository.findByUserIdAndPostIdIn(userId, postIds).stream()
                .map(CommunityPostLike::getPostId)
                .collect(Collectors.toSet());
        Set<Long> bookmarked = communityPostBookmarkRepository.findByUserIdAndPostIdIn(userId, postIds).stream()
                .map(CommunityPostBookmark::getPostId)
                .collect(Collectors.toSet());
        List<Long> blockedUserIds = communityBlockService.blockedUserIds(userId);
        return new CommunityInteractionsResponse(liked, bookmarked, blockedUserIds);
    }
}
