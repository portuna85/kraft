package com.kraft.home;

import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.post.PostCategory;
import com.kraft.winningnumber.RoundFreshnessResponse;
import com.kraft.winningnumber.WinningNumberQueryService;
import com.kraft.winningnumber.WinningNumberResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("홈 요약 서비스 단위 테스트")
class HomeSummaryServiceTest {

    @Mock
    private WinningNumberQueryService winningNumberQueryService;

    @Mock
    private CommunityPostRepository communityPostRepository;

    private HomeSummaryService service;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new HomeSummaryService(winningNumberQueryService, communityPostRepository, CLOCK);
    }

    @Test
    @DisplayName("최신 회차가 없으면 신선도 없이 빈 목록을 반환한다")
    void summarize_noLatestRound_returnsEmptyWithoutFreshness() {
        given(winningNumberQueryService.findLatest()).willReturn(Optional.empty());
        given(communityPostRepository.findAllByOrderByCreatedAtDesc(any())).willReturn(List.of());
        given(communityPostRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(), any())).willReturn(List.of());

        HomeResponse response = service.summarize();

        assertThat(response.latestRound()).isNull();
        assertThat(response.freshness()).isNull();
        assertThat(response.latestPosts()).isEmpty();
        assertThat(response.weeklyPopularPosts()).isEmpty();
    }

    @Test
    @DisplayName("최신 회차가 있으면 신선도와 커뮤니티 글 요약을 함께 반환한다")
    void summarize_withLatestRound_returnsFreshnessAndPosts() {
        WinningNumberResponse latest = new WinningNumberResponse(
                1234, LocalDate.of(2026, 7, 25), List.of(1, 2, 3, 4, 5, 6), 7, 1_000_000_000L, 0L, 0, 0L, 0L);
        given(winningNumberQueryService.findLatest()).willReturn(Optional.of(latest));
        given(winningNumberQueryService.getFreshness()).willReturn(
                new RoundFreshnessResponse(1234, LocalDate.of(2026, 7, 25), true, ZonedDateTime.now(CLOCK)));

        CommunityPost post = new CommunityPost(1L, "글쓴이", "제목", "내용", PostCategory.GENERAL, null,
                OffsetDateTime.now(CLOCK), OffsetDateTime.now(CLOCK));
        given(communityPostRepository.findAllByOrderByCreatedAtDesc(any())).willReturn(List.of(post));
        given(communityPostRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(), any())).willReturn(List.of(post));

        HomeResponse response = service.summarize();

        assertThat(response.latestRound().round()).isEqualTo(1234);
        assertThat(response.freshness().fresh()).isTrue();
        assertThat(response.latestPosts()).hasSize(1);
        assertThat(response.weeklyPopularPosts()).hasSize(1);
    }
}
