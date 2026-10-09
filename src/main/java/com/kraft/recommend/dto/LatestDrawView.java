package com.kraft.recommend.dto;

import com.kraft.recommend.domain.LottoPrizeTax;
import com.kraft.recommend.domain.WinningDraw;

import java.time.LocalDate;
import java.util.List;

/**
 * 화면이 그대로 쓰는 최신 회차 값. 번호 추천 화면이 같은 조각({@code templates/lotto/latest-draw.html})으로 그리며 이 타입
 * 하나를 모델에 {@code latestDraw}로 넣는다.
 * <p>
 * 없는 값은 null이라 템플릿이 그 조각만 건너뛴다(추첨일·보너스·당첨금은 V21 이후 회차에만 있다).
 * {@code takeHomeAmount}는 원천징수 세율로 계산한 표시용 추정치다({@link LottoPrizeTax}).
 */
public record LatestDrawView(int roundNo, List<Integer> numbers, LocalDate drawDate, Integer bonusNo,
                             Long firstPrizeAmount, Long takeHomeAmount, Integer winnerCount) {

    public static LatestDrawView from(WinningDraw draw) {
        Long prize = draw.getFirstPrizeAmount();
        return new LatestDrawView(draw.getRoundNo(), draw.numbers(), draw.getDrawDate(), draw.getBonusNo(),
                prize, prize == null ? null : LottoPrizeTax.afterTax(prize), draw.getFirstPrizeWinnerCount());
    }
}
