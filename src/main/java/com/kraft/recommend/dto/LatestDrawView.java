package com.kraft.recommend.dto;

import com.kraft.recommend.domain.LottoPrizeTax;
import com.kraft.recommend.domain.WinningDraw;

import java.time.LocalDate;
import java.util.List;

/**
 * 화면이 그대로 쓰는 최신 회차 값. 홈과 번호 추천 화면이 같은 조각({@code templates/lotto/latest-draw.html})으로
 * 그리므로 둘 다 이 타입 하나를 모델에 {@code latestDraw}로 넣는다. 예전에는 홈이 이 값을 레코드로, 추천 화면이
 * 속성 7개로 따로 들고 있어 라벨과 클래스 이름이 화면마다 달라졌다.
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
