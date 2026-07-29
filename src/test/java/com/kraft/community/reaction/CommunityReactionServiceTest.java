package com.kraft.community.reaction;

import com.kraft.common.error.ApiException;
import com.kraft.community.block.CommunityBlockService;
import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.post.PostCategory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("커뮤니티 좋아요·북마크 서비스 단위 테스트")
class CommunityReactionServiceTest {

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CommunityPostLikeRepository communityPostLikeRepository;

    @Mock
    private CommunityPostBookmarkRepository communityPostBookmarkRepository;

    @Mock
    private CommunityPostMetricsRepository communityPostMetricsRepository;

    @Mock
    private CommunityBlockService communityBlockService;

    @Mock
    private CommunityReactionWriter communityReactionWriter;

    private CommunityReactionService service;
    private SimpleMeterRegistry meterRegistry;

    private static CommunityPost publishedPost() {
        return new CommunityPost(1L, "글쓴이", "제목", "내용", PostCategory.GENERAL, null,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);
        meterRegistry = new SimpleMeterRegistry();
        service = new CommunityReactionService(communityPostRepository, communityPostLikeRepository,
                communityPostBookmarkRepository, communityPostMetricsRepository, communityBlockService,
                communityReactionWriter, clock, meterRegistry);
    }

    @Test
    @DisplayName("좋아요가 없으면 새로 만들고 집계를 원자적으로 증가시킨다")
    void like_whenAbsent_createsAndIncrements() {
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(publishedPost()));
        given(communityPostLikeRepository.findByPostIdAndUserId(1L, 10L)).willReturn(Optional.empty());

        service.like(1L, 10L);

        verify(communityReactionWriter).insertLike(eq(1L), eq(10L), any());
        verify(communityPostMetricsRepository).incrementLikeCount(1L);
    }

    @Test
    @DisplayName("이미 좋아요를 눌렀으면 멱등하게 아무 것도 하지 않는다(REACTION_ALREADY_APPLIED 흡수)")
    void like_whenAlreadyLiked_isIdempotent() {
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(publishedPost()));
        given(communityPostLikeRepository.findByPostIdAndUserId(1L, 10L))
                .willReturn(Optional.of(new CommunityPostLike(1L, 10L, Instant.now().atOffset(ZoneOffset.UTC))));

        service.like(1L, 10L);

        verify(communityReactionWriter, never()).insertLike(anyLong(), anyLong(), any());
        verify(communityPostMetricsRepository, never()).incrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("B-P0-5: 존재하지 않는 게시글에는 좋아요를 남길 수 없다(FK 500 대신 404)")
    void like_postNotFound_throwsNotFound() {
        given(communityPostRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.like(1L, 10L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_POST_NOT_FOUND");
                });
        verify(communityPostLikeRepository, never()).findByPostIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("KB-02: 좋아요 exists 확인과 insert 사이 경쟁이 나면(writer가 DIVE 전파) 500 대신 멱등하게 흡수한다")
    void like_raceLostAfterCheck_absorbsIdempotently() {
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(publishedPost()));
        given(communityPostLikeRepository.findByPostIdAndUserId(1L, 10L)).willReturn(Optional.empty());
        willThrow(new DataIntegrityViolationException("uk_community_post_likes_post_user"))
                .given(communityReactionWriter).insertLike(eq(1L), eq(10L), any());

        service.like(1L, 10L);

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
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(publishedPost()));
        given(communityPostBookmarkRepository.findByPostIdAndUserId(1L, 10L)).willReturn(Optional.empty());

        service.bookmark(1L, 10L);

        verify(communityReactionWriter).insertBookmark(eq(1L), eq(10L), any());
        assertThat(meterRegistry.get("kraft_community_reaction_created_total").tag("type", "bookmark")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("이미 북마크했으면 멱등하게 아무 것도 하지 않는다")
    void bookmark_whenAlready_isIdempotent() {
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(publishedPost()));
        given(communityPostBookmarkRepository.findByPostIdAndUserId(1L, 10L))
                .willReturn(Optional.of(new CommunityPostBookmark(1L, 10L, Instant.now().atOffset(ZoneOffset.UTC))));

        service.bookmark(1L, 10L);

        verify(communityReactionWriter, never()).insertBookmark(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("B-P0-5: 존재하지 않는 게시글에는 북마크를 남길 수 없다")
    void bookmark_postNotFound_throwsNotFound() {
        given(communityPostRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookmark(1L, 10L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("COMMUNITY_POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("KB-02: 북마크 exists 확인과 insert 사이 경쟁이 나면(writer가 DIVE 전파) 500 대신 멱등하게 흡수한다")
    void bookmark_raceLostAfterCheck_absorbsIdempotently() {
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(publishedPost()));
        given(communityPostBookmarkRepository.findByPostIdAndUserId(1L, 10L)).willReturn(Optional.empty());
        willThrow(new DataIntegrityViolationException("uk_community_post_bookmarks_post_user"))
                .given(communityReactionWriter).insertBookmark(eq(1L), eq(10L), any());

        service.bookmark(1L, 10L);

        assertThat(meterRegistry.get("kraft_community_reaction_created_total").tag("type", "bookmark")
                .counter().count()).isEqualTo(0.0);
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
