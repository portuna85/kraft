package com.kraft.recommend.service;

/**
 * 당첨 회차 이력이 바뀌었다(수입·정정 반영이 끝났다). 이력을 바탕으로 계산해 캐시해 둔 값
 * (홈의 과거 기록 요약 등)이 커밋 직후 비울 수 있게 알리는 이벤트다 — 수입 코드가 그 캐시들을
 * 알 필요가 없게 한다(BE-17).
 *
 * @param verifiedThroughRound 이번 수입으로 검증된 마지막 회차
 */
public record RecommendationHistoryChanged(int verifiedThroughRound) {
}
