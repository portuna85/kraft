package com.kraft.home;

import com.kraft.winningnumber.RoundFreshnessResponse;
import com.kraft.winningnumber.WinningNumberResponse;
import java.util.List;

/**
 * 홈 화면 단일 조회 응답. 개인화 필드는 포함하지 않는다(문서 13.1절) — 로그인 여부와 무관하게
 * 동일한 응답을 캐시할 수 있어야 한다.
 *
 * weeklyPopularPosts: Phase 3 랭킹(좋아요·댓글 가중 점수)이 아직 없으므로, 현재는 "최근 7일
 * 이내 최신순"으로 채운다. 필드명과 의미(이번 주 눈에 띄는 글)는 최종 형태로 고정해뒀으므로,
 * Phase 3에서 백엔드 정렬 로직만 교체하면 프런트엔드 변경 없이 실제 인기 랭킹으로 전환된다.
 */
public record HomeResponse(
        WinningNumberResponse latestRound,
        RoundFreshnessResponse freshness,
        List<HomeCommunityPostSummary> latestPosts,
        List<HomeCommunityPostSummary> weeklyPopularPosts
) {
}
