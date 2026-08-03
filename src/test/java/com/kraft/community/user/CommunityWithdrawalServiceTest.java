package com.kraft.community.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kraft.common.error.ApiException;
import com.kraft.common.account.AccountDataDeletionHandler;
import com.kraft.community.block.CommunityUserBlockRepository;
import com.kraft.community.comment.CommunityComment;
import com.kraft.community.comment.CommunityCommentRepository;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.reaction.CommunityPostBookmarkRepository;
import com.kraft.community.reaction.CommunityPostLike;
import com.kraft.community.reaction.CommunityPostLikeRepository;
import com.kraft.community.report.CommunityReportRepository;
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
    @Mock AccountDataDeletionHandler accountDataDeletionHandler;

    private CommunityWithdrawalService service;

    @BeforeEach
    void setUp() {
        service = new CommunityWithdrawalService(users, posts, comments, metrics, likes, bookmarks, reports, blocks,
                List.of(accountDataDeletionHandler), Clock.systemUTC());
    }

    @Test
    void withdraw_deletesAccountOwnedData() {
        CommunityUser user = new CommunityUser("google", "sub-1", "user", "https://img", OffsetDateTime.now());
        setId(user, 42L);
        given(users.lockById(42L)).willReturn(Optional.of(user));
        given(likes.findByUserId(42L)).willReturn(List.of());
        given(comments.findByOwnerId(42L)).willReturn(List.of());
        given(posts.findByOwnerId(42L)).willReturn(List.of());

        service.withdraw(42L);

        verify(accountDataDeletionHandler).deleteForAccount(42L);
        verify(bookmarks).deleteByUserId(42L);
        verify(reports).deleteByReporterUserId(42L);
        verify(blocks).deleteByBlockerUserIdOrBlockedUserId(42L, 42L);
        verify(users).delete(user);
    }

    // M-8: 좋아요·댓글마다 delete/save+감산을 반복 호출하던 N+1을 배치 호출로 바꿨다 —
    // 게시글별로 올바른 개수만큼 감산되는지(특히 같은 게시글에 댓글이 여러 개인 경우)
    // 검증한다.
    @Test
    void withdraw_batchesLikeAndCommentCleanup() {
        CommunityUser user = new CommunityUser("google", "sub-1", "user", "https://img", OffsetDateTime.now());
        setId(user, 42L);
        given(users.lockById(42L)).willReturn(Optional.of(user));
        given(likes.findByUserId(42L)).willReturn(List.of(
                new CommunityPostLike(1L, 42L, OffsetDateTime.now()),
                new CommunityPostLike(2L, 42L, OffsetDateTime.now())));
        // 게시글 1에는 댓글이 2개(모두 미삭제), 게시글 2에는 1개(이미 삭제됨 — 감산 대상 아님).
        CommunityComment liveCommentA = new CommunityComment(1L, null, 42L, "user", "내용1", OffsetDateTime.now());
        CommunityComment liveCommentB = new CommunityComment(1L, null, 42L, "user", "내용2", OffsetDateTime.now());
        CommunityComment alreadyDeleted = new CommunityComment(2L, null, 42L, "user", "내용3", OffsetDateTime.now());
        alreadyDeleted.eraseForAccountDeletion();
        given(comments.findByOwnerId(42L)).willReturn(List.of(liveCommentA, liveCommentB, alreadyDeleted));
        given(posts.findByOwnerId(42L)).willReturn(List.of());

        service.withdraw(42L);

        verify(likes).deleteByUserId(42L);
        verify(metrics).decrementLikeCountForPosts(List.of(1L, 2L));
        verify(comments).saveAllAndFlush(List.of(liveCommentA, liveCommentB, alreadyDeleted));
        verify(metrics).decrementCommentCountBy(1L, 2);
        org.mockito.Mockito.verify(metrics, org.mockito.Mockito.never()).decrementCommentCountBy(
                org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.anyInt());
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
