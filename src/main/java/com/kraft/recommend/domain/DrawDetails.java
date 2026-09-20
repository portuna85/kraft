package com.kraft.recommend.domain;

import java.time.LocalDate;

/**
 * 최신 회차 화면 표시 전용 부가 정보(HIST-01 제외 판정에는 관여하지 않는다 — 그 판정은
 * {@link WinningDraw#mask()}가 본번호 6개만으로 수행한다). 필드는 전부 nullable이다 —
 * 동행복권 응답 중 일부만 해석 가능한 경우(예: 추첨일 형식만 깨짐)를 표현해야 하기 때문이다.
 */
public record DrawDetails(Integer bonusNo, LocalDate drawDate,
                           Integer firstPrizeWinnerCount, Long firstPrizeAmount) {
}
