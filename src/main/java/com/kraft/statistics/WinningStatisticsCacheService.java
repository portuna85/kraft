package com.kraft.statistics;

import com.kraft.common.lotto.BallClassification;
import com.kraft.common.lotto.SumBuckets;
import com.kraft.winningnumber.FirstPrizeHistoryDto;
import com.kraft.winningnumber.FirstPrizeHistoryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WinningStatisticsCacheService {

    private final FirstPrizeHistoryService firstPrizeHistoryService;

    public WinningStatisticsCacheService(FirstPrizeHistoryService firstPrizeHistoryService) {
        this.firstPrizeHistoryService = firstPrizeHistoryService;
    }

    public AnalysisResponse analyze(List<Integer> rawNumbers) {
        List<Integer> numbers = rawNumbers.stream().sorted().toList();

        int oddCount = (int) numbers.stream().filter(BallClassification::isOdd).count();
        int evenCount = numbers.size() - oddCount;
        int highCount = (int) numbers.stream().filter(BallClassification::isHigh).count();
        int lowCount = numbers.size() - highCount;
        int sum = numbers.stream().mapToInt(Integer::intValue).sum();
        String sumBucket = SumBuckets.bucketOf(sum);

        int consecutivePairCount = 0;
        for (int i = 0; i < numbers.size() - 1; i++) {
            if (numbers.get(i + 1) - numbers.get(i) == 1) {
                consecutivePairCount++;
            }
        }

        List<AnalysisResponse.RangeDistribution> ranges = computeRangeDistribution(numbers);
        List<FirstPrizeHistoryDto> history = firstPrizeHistoryService.findByNumbers(numbers);

        return new AnalysisResponse(numbers, oddCount, evenCount, lowCount, highCount,
                sum, sumBucket, consecutivePairCount, ranges, !history.isEmpty(), history);
    }

    private static List<AnalysisResponse.RangeDistribution> computeRangeDistribution(List<Integer> numbers) {
        int[] ranges = new int[5];
        for (int n : numbers) {
            if (n <= 9) {
                ranges[0]++;
            } else if (n <= 19) {
                ranges[1]++;
            } else if (n <= 29) {
                ranges[2]++;
            } else if (n <= 39) {
                ranges[3]++;
            } else {
                ranges[4]++;
            }
        }
        return List.of(
                new AnalysisResponse.RangeDistribution("1-9", ranges[0]),
                new AnalysisResponse.RangeDistribution("10-19", ranges[1]),
                new AnalysisResponse.RangeDistribution("20-29", ranges[2]),
                new AnalysisResponse.RangeDistribution("30-39", ranges[3]),
                new AnalysisResponse.RangeDistribution("40-45", ranges[4])
        );
    }
}
