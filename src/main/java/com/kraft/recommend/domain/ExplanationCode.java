package com.kraft.recommend.domain;

/**
 * balanced 전략이 실제로 충족한 조건만 응답에 담는 설명 코드(03문서 4절 POL-BALANCED).
 * 점수를 당첨 확률로 변환하지 않는다 — 단순 형태 조건 충족 여부다.
 */
public enum ExplanationCode {
    ODD_EVEN_BALANCED,
    LOW_HIGH_BALANCED,
    SUM_IN_RANGE,
    CONSECUTIVE_PAIR_LIMITED,
    DECADE_SPREAD,
}
