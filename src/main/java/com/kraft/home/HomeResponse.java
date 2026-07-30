package com.kraft.home;

import com.kraft.winningnumber.RoundFreshnessResponse;
import com.kraft.winningnumber.WinningNumberResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 홈 화면 단일 조회 응답. 개인화 필드는 포함하지 않는다 — 로그인 여부와 무관하게
 * 동일한 응답을 캐시할 수 있어야 한다.
 *
 * weeklyPopularPosts: 최근 7일 공개 글을 좋아요·댓글·조회수와 시간 감쇠를 조합한 점수로
 * 정렬한다. 홈과 커뮤니티 목록의 "주간 인기" 표현은 이 동일한 정렬 의미를 가리킨다.
 */
public record HomeResponse(
        @Schema(nullable = true) WinningNumberResponse latestRound,
        @Schema(nullable = true) RoundFreshnessResponse freshness,
        List<HomeCommunityPostSummary> latestPosts,
        List<HomeCommunityPostSummary> weeklyPopularPosts
) {
}
