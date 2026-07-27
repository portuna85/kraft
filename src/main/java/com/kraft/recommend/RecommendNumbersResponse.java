package com.kraft.recommend;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * strategy/algorithmVersion/historyThroughRound: "50개 표본 중 점수가 가장 높은 조합"이라는
 * 실제 알고리즘과 그 근거가 된 이력 회차를 응답에 명시해, 클라이언트가 "당첨 확률을 높인다"가
 * 아니라 검증 가능한 휴리스틱임을 보여줄 수 있게 한다.
 *
 * setId/items/createdAt은 요청에 X-Device-Token이 있을 때만 채워진다(없으면 null) — 기존
 * 호환 클라이언트는 헤더를 보내지 않으므로 이 필드들 없이도 동작이 그대로 유지된다.
 */
public record RecommendNumbersResponse(
        List<List<Integer>> recommendations,
        String strategy,
        String algorithmVersion,
        int historyThroughRound,
        Long setId,
        List<RecommendationItemView> items,
        OffsetDateTime createdAt
) {
}
