package com.kraft.community.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kraft.common.error.ApiException;
import com.kraft.common.account.AccountDataDeletionHandler;
import com.kraft.community.block.CommunityUserBlockRepository;
import com.kraft.community.comment.CommunityCommentRepository;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.reaction.CommunityPostBookmarkRepository;
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
