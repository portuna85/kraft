package com.kraft.community.user;

import com.kraft.common.error.ApiException;
import com.kraft.common.account.AccountDataDeletionHandler;
import com.kraft.community.block.CommunityUserBlockRepository;
import com.kraft.community.comment.CommunityComment;
import com.kraft.community.comment.CommunityCommentRepository;
import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.reaction.CommunityPostBookmarkRepository;
import com.kraft.community.reaction.CommunityPostLikeRepository;
import com.kraft.community.report.CommunityReportRepository;
import com.kraft.community.report.ReportTargetType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."));

        communityPostLikeRepository.findByUserId(userId).forEach(like -> {
            communityPostLikeRepository.deleteByPostIdAndUserId(like.getPostId(), userId);
            communityPostMetricsRepository.decrementLikeCount(like.getPostId());
        });
        communityPostBookmarkRepository.deleteByUserId(userId);
        communityReportRepository.deleteByReporterUserId(userId);
        communityReportRepository.deleteByTargetTypeAndTargetId(ReportTargetType.USER, userId);
        communityUserBlockRepository.deleteByBlockerUserIdOrBlockedUserId(userId, userId);

        List<CommunityComment> comments = communityCommentRepository.findByOwnerId(userId);
        for (CommunityComment comment : comments) {
            boolean decrementCommentCount = !comment.isDeleted();
            comment.eraseForAccountDeletion();
            communityCommentRepository.saveAndFlush(comment);
            if (decrementCommentCount) {
                communityPostMetricsRepository.decrementCommentCount(comment.getPostId());
            }
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<CommunityPost> posts = communityPostRepository.findByOwnerId(userId);
        posts.forEach(post -> post.eraseForAccountDeletion(now));
        communityPostRepository.saveAllAndFlush(posts);

        accountDataDeletionHandlers.forEach(handler -> handler.deleteForAccount(userId));
        communityUserRepository.delete(user);
    }
}
