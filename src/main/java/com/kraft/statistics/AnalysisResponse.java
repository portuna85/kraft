package com.kraft.statistics;

import com.kraft.winningnumber.FirstPrizeHistoryDto;
import java.util.List;

public record AnalysisResponse(
        List<Integer> numbers,
        int oddCount,
        int evenCount,
        int lowCount,
        int highCount,
        int sumOfNumbers,
        String sumBucket,
        int consecutivePairCount,
        List<RangeDistribution> rangeDistribution,
        boolean wonFirstPrize,
        List<FirstPrizeHistoryDto> firstPrizeHistory
) {
    public record RangeDistribution(String range, int count) {
    }
}
