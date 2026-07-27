package com.kraft.recommend;

import java.util.List;

/** 추천 조합 1개에 대한 상세 뷰 — 위치, 번호, (있는 경우) 점수와 근거 코드. */
public record RecommendationItemView(
        int position,
        List<Integer> numbers,
        Integer score,
        List<ExplanationCode> explanationCodes
) {
}
