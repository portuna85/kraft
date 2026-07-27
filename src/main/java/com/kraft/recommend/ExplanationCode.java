package com.kraft.recommend;

/**
 * 추천 조합이 특정 전략의 어떤 조건을 만족했는지 나타내는 안정적인 코드.
 * 프런트엔드는 이 코드를 한국어 문구로 매핑해 표시한다 — 문구가 바뀌어도 이 이름은
 * 바뀌지 않아야 API 계약이 유지된다.
 */
public enum ExplanationCode {
    /** 홀짝 번호 개수가 2:4~4:2 범위 안에 있다. */
    ODD_EVEN_BALANCED,
    /** 1~22와 23~45 구간의 번호 개수가 2:4~4:2 범위 안에 있다. */
    LOW_HIGH_BALANCED,
    /** 번호 합계가 100~180 범위 안에 있다. */
    SUM_IN_RANGE,
    /** 연속된 번호 쌍이 1개 이하다. */
    CONSECUTIVE_PAIR_LIMITED,
    /** 1~10, 11~20, 21~30, 31~40, 41~45 다섯 구간 중 4개 이상을 사용했다. */
    DECADE_SPREAD
}
