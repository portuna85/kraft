package com.kraft.community.user;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.common.account.AccountDataDeletionHandler;
import com.kraft.community.block.CommunityUserBlockRepository;
import com.kraft.community.comment.CommunityCommentRepository;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.reaction.CommunityPostBookmarkRepository;
import com.kraft.community.reaction.CommunityPostLike;
import com.kraft.community.reaction.CommunityPostLikeRepository;
import com.kraft.community.report.CommunityReportRepository;
import com.kraft.community.report.ReportTargetType;
import com.kraft.recommend.RecommendationSetRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
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
    private final RecommendationSetRepository recommendationSetRepository;
    private final List<AccountDataDeletionHandler> accountDataDeletionHandlers;
    private final Clock clock;

    // BE-02(docs/improvement.md): recommend가 여러 세트를 한 번에 지울 때 IN 절
    // 파라미터 수가 DB/드라이버 상한에 가까워지지 않도록 청크로 나눈다 —
    // RecommendationAccountDataDeletionHandler의 CHUNK_SIZE와 같은 값이다.
    private static final int RECOMMENDATION_SET_CHUNK_SIZE = 500;

    public CommunityWithdrawalService(CommunityUserRepository communityUserRepository,
                                       CommunityPostRepository communityPostRepository,
                                       CommunityCommentRepository communityCommentRepository,
                                       CommunityPostMetricsRepository communityPostMetricsRepository,
                                       CommunityPostLikeRepository communityPostLikeRepository,
                                       CommunityPostBookmarkRepository communityPostBookmarkRepository,
                                       CommunityReportRepository communityReportRepository,
                                       CommunityUserBlockRepository communityUserBlockRepository,
                                       RecommendationSetRepository recommendationSetRepository,
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
        this.recommendationSetRepository = recommendationSetRepository;
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

        // DATA-DEL-01(docs/improvement.md): 이전에는 댓글 전체를 엔티티로 읽어
        // eraseForAccountDeletion()으로 각 필드를 바꾼 뒤 saveAllAndFlush했다 — 계정 하나의
        // 댓글 수가 많으면 영속성 컨텍스트·트랜잭션 힙이 무제한으로 커진다. 감산량은 벌크
        // UPDATE 전에(아직 deleted=false인 상태에서) 먼저 계산해야 한다 — 순서를 바꾸면
        // 전부 deleted=true가 된 뒤라 카운트가 0이 된다.
        List<CommunityCommentRepository.PostCommentCount> commentCounts =
                communityCommentRepository.countNonDeletedGroupedByPostForOwner(userId);
        communityCommentRepository.eraseForAccountDeletion(userId);
        commentCounts.forEach(entry ->
                communityPostMetricsRepository.decrementCommentCountBy(entry.getPostId(), (int) entry.getCount()));

        OffsetDateTime now = OffsetDateTime.now(clock);
        communityPostRepository.eraseForAccountDeletion(userId, now);

        // BE-02(docs/improvement.md): eraseForAccountDeletion은 탈퇴자 "본인 소유"
        // 게시글의 recommendation_set_id만 끊는다. 그런데 탈퇴자가 소유한 세트는
        // IdentityMergeService.claim()으로 다른 계정에 첨부된 채로 남을 수 있다.
        // RecommendationAccountDataDeletionHandler(아래 forEach)가 이 세트들을 곧 삭제할
        // 텐데 recommendation_set_id는 ON DELETE RESTRICT(V21)이므로, 다른 계정 게시글이
        // 참조를 들고 있으면 그 삭제가 DataIntegrityViolationException(500)이 된다. 세트
        // 삭제 전에 소유자와 무관하게 참조를 전부 끊는다 — 세트 자체는 이어서 삭제되므로
        // 남는 개인정보는 없다.
        List<Long> ownedRecommendationSetIds = recommendationSetRepository.findIdsByOwnerUserId(userId);
        for (int start = 0; start < ownedRecommendationSetIds.size(); start += RECOMMENDATION_SET_CHUNK_SIZE) {
            List<Long> chunk = ownedRecommendationSetIds.subList(start,
                    Math.min(start + RECOMMENDATION_SET_CHUNK_SIZE, ownedRecommendationSetIds.size()));
            communityPostRepository.detachRecommendationSetIds(chunk);
        }

        accountDataDeletionHandlers.forEach(handler -> handler.deleteForAccount(userId));
        communityUserRepository.delete(user);
    }
}
