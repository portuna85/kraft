package com.kraft.statistics;

import com.kraft.winningnumber.FirstPrizeHistoryDto;
import java.util.List;

public record RankedCombinationDto(
        List<BallFrequencyDto> balls,
        boolean wonFirstPrize,
        List<FirstPrizeHistoryDto> firstPrizeHistory
) {
    public RankedCombinationDto(List<BallFrequencyDto> balls, List<FirstPrizeHistoryDto> firstPrizeHistory) {
        this(balls, !firstPrizeHistory.isEmpty(), firstPrizeHistory);
    }
}
