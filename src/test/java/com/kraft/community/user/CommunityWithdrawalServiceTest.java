package com.kraft.community.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kraft.common.error.ApiException;
import com.kraft.common.account.AccountDataDeletionHandler;
import com.kraft.community.block.CommunityUserBlockRepository;
import com.kraft.community.comment.CommunityCommentRepository;
import com.kraft.community.comment.CommunityCommentRepository.PostCommentCount;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.reaction.CommunityPostBookmarkRepository;
import com.kraft.community.reaction.CommunityPostLike;
import com.kraft.community.reaction.CommunityPostLikeRepository;
import com.kraft.community.report.CommunityReportRepository;
import com.kraft.recommend.RecommendationSetRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CommunityWithdrawalServiceTest {
    @Mock CommunityUserRepository users;
    @Mock CommunityPostRepository posts;
    @Mock CommunityCommentRepository comments;
    @Mock CommunityPostMetricsRepository metrics;
    @Mock CommunityPostLikeRepository likes;
    @Mock CommunityPostBookmarkRepository bookmarks;
    @Mock CommunityReportRepository reports;
    @Mock CommunityUserBlockRepository blocks;
    @Mock RecommendationSetRepository recommendationSets;
    @Mock AccountDataDeletionHandler accountDataDeletionHandler;

    private CommunityWithdrawalService service;

    @BeforeEach
    void setUp() {
        service = new CommunityWithdrawalService(users, posts, comments, metrics, likes, bookmarks, reports, blocks,
                recommendationSets, List.of(accountDataDeletionHandler), Clock.systemUTC());
    }

    @Test
    void withdraw_deletesAccountOwnedData() {
        CommunityUser user = new CommunityUser("google", "sub-1", "user", "https://img", OffsetDateTime.now());
        setId(user, 42L);
        given(users.lockById(42L)).willReturn(Optional.of(user));
        given(likes.findByUserId(42L)).willReturn(List.of());
        given(comments.countNonDeletedGroupedByPostForOwner(42L)).willReturn(List.of());
        given(recommendationSets.findIdsByOwnerUserId(42L)).willReturn(List.of());

        service.withdraw(42L);

        verify(accountDataDeletionHandler).deleteForAccount(42L);
        verify(bookmarks).deleteByUserId(42L);
        verify(reports).deleteByReporterUserId(42L);
        verify(blocks).deleteByBlockerUserIdOrBlockedUserId(42L, 42L);
        verify(comments).eraseForAccountDeletion(42L);
        verify(posts).eraseForAccountDeletion(org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class));
        verify(users).delete(user);
    }

    // BE-02(docs/improvement.md): 탈퇴자가 소유했던 세트를 다른 계정의 게시글이 여전히
    // 참조할 수 있다(claim으로 소유권이 넘어간 세트). RecommendationAccountDataDeletionHandler가
    // 그 세트를 삭제하기 전에, 소유자 무관하게 참조를 끊는 detachRecommendationSetIds가
    // 실제로 호출되는지 검증한다 — 안 그러면 ON DELETE RESTRICT(V21)로 500이 난다.
    @Test
    void withdraw_detachesOwnedRecommendationSetReferencesBeforeDeletionHandlers() {
        CommunityUser user = new CommunityUser("google", "sub-1", "user", "https://img", OffsetDateTime.now());
        setId(user, 42L);
        given(users.lockById(42L)).willReturn(Optional.of(user));
        given(likes.findByUserId(42L)).willReturn(List.of());
        given(comments.countNonDeletedGroupedByPostForOwner(42L)).willReturn(List.of());
        given(recommendationSets.findIdsByOwnerUserId(42L)).willReturn(List.of(7L, 8L));

        service.withdraw(42L);

        verify(posts).detachRecommendationSetIds(List.of(7L, 8L));
    }

    // DATA-DEL-01(docs/improvement.md): 좋아요·댓글마다 delete/save+감산을 반복 호출하던
    // 것을 벌크 UPDATE/DELETE로 바꿨다 — 게시글별로 올바른 개수만큼 감산되는지(특히 같은
    // 게시글에 댓글이 여러 개인 경우) 검증한다. 이미 삭제된 댓글은 감산 대상이 아니라는
    // 계약은 이제 repository의 countNonDeletedGroupedByPostForOwner 쿼리 자체(deleted=false
    // 필터)가 진다 — 이 테스트는 서비스가 그 결과를 그대로 감산에 반영하는지만 본다.
    @Test
    void withdraw_batchesLikeAndCommentCleanup() {
        CommunityUser user = new CommunityUser("google", "sub-1", "user", "https://img", OffsetDateTime.now());
        setId(user, 42L);
        given(users.lockById(42L)).willReturn(Optional.of(user));
        given(likes.findByUserId(42L)).willReturn(List.of(
                new CommunityPostLike(1L, 42L, OffsetDateTime.now()),
                new CommunityPostLike(2L, 42L, OffsetDateTime.now())));
        // 게시글 1에는 미삭제 댓글이 2개(감산 대상), 게시글 2는 결과에 없다(이미 삭제된
        // 댓글뿐이라 쿼리가 애초에 postId 2를 돌려주지 않는다).
        given(comments.countNonDeletedGroupedByPostForOwner(42L)).willReturn(List.of(postCommentCount(1L, 2)));

        service.withdraw(42L);

        verify(likes).deleteByUserId(42L);
        verify(metrics).decrementLikeCountForPosts(List.of(1L, 2L));
        verify(comments).eraseForAccountDeletion(42L);
        verify(metrics).decrementCommentCountBy(1L, 2);
        org.mockito.Mockito.verify(metrics, org.mockito.Mockito.never()).decrementCommentCountBy(
                org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.anyInt());
    }

    private static PostCommentCount postCommentCount(Long postId, long count) {
        return new PostCommentCount() {
            @Override
            public Long getPostId() {
                return postId;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    @Test
    void withdraw_unknownUser_throwsNotFound() {
        given(users.lockById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.withdraw(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static void setId(CommunityUser user, Long id) {
        try {
            var field = CommunityUser.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
