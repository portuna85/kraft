package com.kraft.recommend;

import com.kraft.winningnumber.FirstPrizeHistoryDto;
import java.util.List;

public record CombinationCheckResponse(
        boolean wonFirstPrize,
        List<FirstPrizeHistoryDto> firstPrizeHistory
) {
    public CombinationCheckResponse(List<FirstPrizeHistoryDto> firstPrizeHistory) {
        this(!firstPrizeHistory.isEmpty(), firstPrizeHistory);
    }
}
