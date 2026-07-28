package com.kraft.community.reaction;

import com.kraft.community.block.CommunityBlockService;
import com.kraft.community.post.CommunityPostMetricsRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("커뮤니티 좋아요·북마크 서비스 단위 테스트")
class CommunityReactionServiceTest {

    @Mock
    private CommunityPostLikeRepository communityPostLikeRepository;

    @Mock
    private CommunityPostBookmarkRepository communityPostBookmarkRepository;

    @Mock
    private CommunityPostMetricsRepository communityPostMetricsRepository;

    @Mock
    private CommunityBlockService communityBlockService;

    private CommunityReactionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);
        service = new CommunityReactionService(communityPostLikeRepository, communityPostBookmarkRepository,
                communityPostMetricsRepository, communityBlockService, clock, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("좋아요가 없으면 새로 만들고 집계를 원자적으로 증가시킨다")
    void like_whenAbsent_createsAndIncrements() {
        given(communityPostLikeRepository.findByPostIdAndUserId(1L, 10L)).willReturn(Optional.empty());

        service.like(1L, 10L);

        verify(communityPostLikeRepository).save(any());
        verify(communityPostMetricsRepository).incrementLikeCount(1L);
    }

    @Test
    @DisplayName("이미 좋아요를 눌렀으면 멱등하게 아무 것도 하지 않는다(REACTION_ALREADY_APPLIED 흡수)")
    void like_whenAlreadyLiked_isIdempotent() {
        given(communityPostLikeRepository.findByPostIdAndUserId(1L, 10L))
                .willReturn(Optional.of(new CommunityPostLike(1L, 10L, Instant.now().atOffset(ZoneOffset.UTC))));

        service.like(1L, 10L);

        verify(communityPostLikeRepository, never()).save(any());
        verify(communityPostMetricsRepository, never()).incrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("좋아요 취소 시 존재하면 삭제하고 집계를 감소시킨다")
    void unlike_whenPresent_deletesAndDecrements() {
        given(communityPostLikeRepository.findByPostIdAndUserId(1L, 10L))
                .willReturn(Optional.of(new CommunityPostLike(1L, 10L, Instant.now().atOffset(ZoneOffset.UTC))));

        service.unlike(1L, 10L);

        verify(communityPostLikeRepository).deleteByPostIdAndUserId(1L, 10L);
        verify(communityPostMetricsRepository).decrementLikeCount(1L);
    }

    @Test
    @DisplayName("좋아요가 없는 상태에서 취소해도 멱등하게 204로 끝난다")
    void unlike_whenAbsent_isIdempotentNoOp() {
        given(communityPostLikeRepository.findByPostIdAndUserId(1L, 10L)).willReturn(Optional.empty());

        service.unlike(1L, 10L);

        verify(communityPostLikeRepository, never()).deleteByPostIdAndUserId(anyLong(), anyLong());
        verify(communityPostMetricsRepository, never()).decrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("북마크가 없으면 새로 만든다")
    void bookmark_whenAbsent_creates() {
        given(communityPostBookmarkRepository.findByPostIdAndUserId(1L, 10L)).willReturn(Optional.empty());

        service.bookmark(1L, 10L);

        verify(communityPostBookmarkRepository).save(any());
    }

    @Test
    @DisplayName("이미 북마크했으면 멱등하게 아무 것도 하지 않는다")
    void bookmark_whenAlready_isIdempotent() {
        given(communityPostBookmarkRepository.findByPostIdAndUserId(1L, 10L))
                .willReturn(Optional.of(new CommunityPostBookmark(1L, 10L, Instant.now().atOffset(ZoneOffset.UTC))));

        service.bookmark(1L, 10L);

        verify(communityPostBookmarkRepository, never()).save(any());
    }

    @Test
    @DisplayName("북마크 해제는 항상 삭제를 시도하는 멱등 연산이다")
    void unbookmark_alwaysDeletes() {
        service.unbookmark(1L, 10L);

        verify(communityPostBookmarkRepository).deleteByPostIdAndUserId(1L, 10L);
    }

    @Test
    @DisplayName("개인 반응 상태는 좋아요·북마크·차단 목록을 함께 반환한다")
    void interactions_returnsLikedBookmarkedAndBlocked() {
        given(communityPostLikeRepository.findByUserIdAndPostIdIn(10L, List.of(1L, 2L)))
                .willReturn(List.of(new CommunityPostLike(1L, 10L, Instant.now().atOffset(ZoneOffset.UTC))));
        given(communityPostBookmarkRepository.findByUserIdAndPostIdIn(10L, List.of(1L, 2L)))
                .willReturn(List.of(new CommunityPostBookmark(2L, 10L, Instant.now().atOffset(ZoneOffset.UTC))));
        given(communityBlockService.blockedUserIds(10L)).willReturn(List.of(99L));

        CommunityInteractionsResponse response = service.interactions(10L, List.of(1L, 2L));

        assertThat(response.likedPostIds()).containsExactly(1L);
        assertThat(response.bookmarkedPostIds()).containsExactly(2L);
        assertThat(response.blockedUserIds()).containsExactly(99L);
    }
}
