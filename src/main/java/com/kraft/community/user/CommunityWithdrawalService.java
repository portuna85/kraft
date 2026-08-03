package com.kraft.community.user;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.common.account.AccountDataDeletionHandler;
import com.kraft.community.block.CommunityUserBlockRepository;
import com.kraft.community.comment.CommunityComment;
import com.kraft.community.comment.CommunityCommentRepository;
import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.reaction.CommunityPostBookmarkRepository;
import com.kraft.community.reaction.CommunityPostLike;
import com.kraft.community.reaction.CommunityPostLikeRepository;
import com.kraft.community.report.CommunityReportRepository;
import com.kraft.community.report.ReportTargetType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permanently erases account-owned data while preserving other users' conversation structure. */
@Service
public class CommunityWithdrawalService {

    private final CommunityUserRepository communityUserRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityPostMetricsRepository communityPostMetricsRepository;
    private final CommunityPostLikeRepository communityPostLikeRepository;
    private final CommunityPostBookmarkRepository communityPostBookmarkRepository;
    private final CommunityReportRepository communityReportRepository;
    private final CommunityUserBlockRepository communityUserBlockRepository;
    private final List<AccountDataDeletionHandler> accountDataDeletionHandlers;
    private final Clock clock;

    public CommunityWithdrawalService(CommunityUserRepository communityUserRepository,
                                       CommunityPostRepository communityPostRepository,
                                       CommunityCommentRepository communityCommentRepository,
                                       CommunityPostMetricsRepository communityPostMetricsRepository,
                                       CommunityPostLikeRepository communityPostLikeRepository,
                                       CommunityPostBookmarkRepository communityPostBookmarkRepository,
                                       CommunityReportRepository communityReportRepository,
                                       CommunityUserBlockRepository communityUserBlockRepository,
                                       List<AccountDataDeletionHandler> accountDataDeletionHandlers,
                                       Clock clock) {
        this.communityUserRepository = communityUserRepository;
        this.communityPostRepository = communityPostRepository;
        this.communityCommentRepository = communityCommentRepository;
        this.communityPostMetricsRepository = communityPostMetricsRepository;
        this.communityPostLikeRepository = communityPostLikeRepository;
        this.communityPostBookmarkRepository = communityPostBookmarkRepository;
        this.communityReportRepository = communityReportRepository;
        this.communityUserBlockRepository = communityUserBlockRepository;
        this.accountDataDeletionHandlers = accountDataDeletionHandlers;
        this.clock = clock;
    }

    @Transactional
    public void withdraw(Long userId) {
        CommunityUser user = communityUserRepository.lockById(userId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.COMMUNITY_USER_NOT_FOUND,
                        "사용자를 찾을 수 없습니다."));

        // M-8: 좋아요마다 delete+decrement를 반복 호출하던 N+1 — 목록을 한 번 읽고
        // 사용자 단위 벌크 삭제·게시글 IN 절 벌크 감산으로 바꾼다. withdraw()가 대상 사용자
        // 행을 이미 잠갔으므로 findByUserId 이후 이 목록이 바뀔 경합은 없다.
        List<Long> likedPostIds = communityPostLikeRepository.findByUserId(userId).stream()
                .map(CommunityPostLike::getPostId)
                .toList();
        communityPostLikeRepository.deleteByUserId(userId);
        if (!likedPostIds.isEmpty()) {
            communityPostMetricsRepository.decrementLikeCountForPosts(likedPostIds);
        }
        communityPostBookmarkRepository.deleteByUserId(userId);
        communityReportRepository.deleteByReporterUserId(userId);
        communityReportRepository.deleteByTargetTypeAndTargetId(ReportTargetType.USER, userId);
        communityUserBlockRepository.deleteByBlockerUserIdOrBlockedUserId(userId, userId);

        // M-8: 댓글마다 saveAndFlush+decrement를 반복 호출하던 N+1 — 저장은 saveAll로 한 번에,
        // 감산은 게시글별로 묶어 실제 삭제한 개수만큼만 호출한다(좋아요와 달리 한 사용자가
        // 같은 게시글에 댓글을 여러 개 남길 수 있어 게시글당 정확히 1이 아닐 수 있다).
        List<CommunityComment> comments = communityCommentRepository.findByOwnerId(userId);
        Map<Long, Long> commentCountByPostId = comments.stream()
                .filter(comment -> !comment.isDeleted())
                .collect(Collectors.groupingBy(CommunityComment::getPostId, Collectors.counting()));
        comments.forEach(CommunityComment::eraseForAccountDeletion);
        // saveAllAndFlush로 즉시 flush한다 — saveAll만 쓰면 변경이 영속성 컨텍스트에
        // 대기만 하다가, 바로 뒤 decrementCommentCountBy의 clearAutomatically=true 벌크
        // UPDATE가 flush 없이 컨텍스트를 비워 이 댓글 변경 자체가 DB에 반영되지 않고
        // 유실된다(CommunityPostLikeRepository.deleteByPostIdAndUserId 문서와 동일한 함정
        // — 원래 코드는 saveAndFlush를 개별 호출해 우연히 피해갔다).
        communityCommentRepository.saveAllAndFlush(comments);
        commentCountByPostId.forEach((postId, count) ->
                communityPostMetricsRepository.decrementCommentCountBy(postId, count.intValue()));

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<CommunityPost> posts = communityPostRepository.findByOwnerId(userId);
        posts.forEach(post -> post.eraseForAccountDeletion(now));
        communityPostRepository.saveAllAndFlush(posts);

        accountDataDeletionHandlers.forEach(handler -> handler.deleteForAccount(userId));
        communityUserRepository.delete(user);
    }
}
