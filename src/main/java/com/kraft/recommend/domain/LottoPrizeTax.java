package com.kraft.recommend.domain;

/**
 * 복권 당첨금 원천징수(소득세 20%+지방소득세 2%=22%, 3억원 초과분은 30%+3%=33%, 200만원 이하는
 * 비과세) 적용 후 세후 예상 금액. 세율이 전부 정수 퍼센트라 {@code long} 나눗셈이 원 단위 절사와
 * 정확히 일치해 {@code BigDecimal}이 필요 없다.
 * <p>
 * 비과세 기준은 동행복권 공식 안내(https://www.dhlottery.co.kr/guide/wnrGuide, 2026-09-21 확인)를 따라 200만원이다. 법정
 * 확정액이 아니라 화면 표시용 추정치(필요경비·절사 등 세부 규칙 미반영)라 라벨도 "실수령액"이 아니라 "세후 예상 금액"이다.
 */
public final class LottoPrizeTax {

    private static final long TAX_FREE_THRESHOLD = 2_000_000L;
    private static final long BRACKET_THRESHOLD = 300_000_000L;
    private static final long LOWER_RATE_PERCENT = 22L;
    private static final long UPPER_RATE_PERCENT = 33L;

    private LottoPrizeTax() {
    }

    public static long afterTax(long prizeAmount) {
        if (prizeAmount <= TAX_FREE_THRESHOLD) {
            return prizeAmount;
        }
        long tax = prizeAmount <= BRACKET_THRESHOLD
                ? prizeAmount * LOWER_RATE_PERCENT / 100
                : BRACKET_THRESHOLD * LOWER_RATE_PERCENT / 100
                        + (prizeAmount - BRACKET_THRESHOLD) * UPPER_RATE_PERCENT / 100;
        return prizeAmount - tax;
    }
}
