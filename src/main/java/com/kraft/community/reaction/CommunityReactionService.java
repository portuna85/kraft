package com.kraft.community.reaction;

import com.kraft.common.error.ApiException;
import com.kraft.community.block.CommunityBlockService;
import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.post.PostStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommunityReactionService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostLikeRepository communityPostLikeRepository;
    private final CommunityPostBookmarkRepository communityPostBookmarkRepository;
    private final CommunityPostMetricsRepository communityPostMetricsRepository;
    private final CommunityBlockService communityBlockService;
    private final CommunityReactionWriter communityReactionWriter;
    private final Clock clock;
    private final Counter likeCreatedCounter;
    private final Counter bookmarkCreatedCounter;

    public CommunityReactionService(CommunityPostRepository communityPostRepository,
                                     CommunityPostLikeRepository communityPostLikeRepository,
                                     CommunityPostBookmarkRepository communityPostBookmarkRepository,
                                     CommunityPostMetricsRepository communityPostMetricsRepository,
                                     CommunityBlockService communityBlockService,
                                     CommunityReactionWriter communityReactionWriter,
                                     Clock clock,
                                     MeterRegistry meterRegistry) {
        this.communityPostRepository = communityPostRepository;
        this.communityPostLikeRepository = communityPostLikeRepository;
        this.communityPostBookmarkRepository = communityPostBookmarkRepository;
        this.communityPostMetricsRepository = communityPostMetricsRepository;
        this.communityBlockService = communityBlockService;
        this.communityReactionWriter = communityReactionWriter;
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

    /**
     * B-P0-5: 대상 게시글이 존재하고 공개(PUBLISHED) 상태인지 먼저 확인한다 — 이전에는 존재
     * 검증 없이 바로 insert해 FK 위반으로 500이 나거나, 숨김·삭제된 글에도 반응을 남길 수 있었다.
     */
    private void requireVisiblePost(Long postId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND",
                        "게시글을 찾을 수 없습니다."));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");
        }
    }

    /**
     * 멱등 PUT — 이미 좋아요를 눌렀어도 성공으로 흡수한다(REACTION_ALREADY_APPLIED는 오류가
     * 아니다). KB-02: insert 시도 자체는 {@link CommunityReactionWriter}의 REQUIRES_NEW
     * 트랜잭션에 위임한다 — CommunityPostLike는 IDENTITY라 save() 시 즉시 INSERT가 나가는데,
     * 유니크 위반을 이 메서드와 같은 트랜잭션 안에서 catch하면 Hibernate 세션이
     * rollback-only로 마킹돼 커밋 시점에 UnexpectedRollbackException이 날 수 있었다
     * (Mockito mock 테스트만으로는 못 잡던 경로 — Testcontainers 실 MariaDB로 확인). insert를
     * 별도 트랜잭션으로 격리하면 그 실패는 그 트랜잭션만 롤백시키고 원래의
     * DataIntegrityViolationException이 그대로 여기까지 전파되므로, 이 메서드가 속한
     * (외부) 트랜잭션을 물들이지 않고 안전하게 catch해 멱등 흡수할 수 있다.
     */
    public void like(Long postId, Long userId) {
        requireVisiblePost(postId);
        if (communityPostLikeRepository.findByPostIdAndUserId(postId, userId).isEmpty()) {
            try {
                communityReactionWriter.insertLike(postId, userId, OffsetDateTime.now(clock));
                communityPostMetricsRepository.incrementLikeCount(postId);
                likeCreatedCounter.increment();
            } catch (DataIntegrityViolationException concurrentLike) {
                // B-P0-4/KB-02: exists 확인과 insert 사이에 동시 요청이 먼저 좋아요를 남긴 경쟁 —
                // uk_community_post_likes_post_user 위반을 500으로 흘려보내지 않고 멱등하게 흡수한다.
            }
        }
    }

    /** 멱등 DELETE — 좋아요가 없었어도 그냥 성공(204)으로 처리한다. */
    public void unlike(Long postId, Long userId) {
        if (communityPostLikeRepository.findByPostIdAndUserId(postId, userId).isPresent()) {
            communityPostLikeRepository.deleteByPostIdAndUserId(postId, userId);
            communityPostMetricsRepository.decrementLikeCount(postId);
        }
    }

    /** KB-02: like()와 같은 이유로 insert 시도를 CommunityReactionWriter의 REQUIRES_NEW에 위임한다. */
    public void bookmark(Long postId, Long userId) {
        requireVisiblePost(postId);
        if (communityPostBookmarkRepository.findByPostIdAndUserId(postId, userId).isEmpty()) {
            try {
                communityReactionWriter.insertBookmark(postId, userId, OffsetDateTime.now(clock));
                bookmarkCreatedCounter.increment();
            } catch (DataIntegrityViolationException concurrentBookmark) {
                // B-P0-4/KB-02: like()와 동일한 경쟁 흡수.
            }
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
