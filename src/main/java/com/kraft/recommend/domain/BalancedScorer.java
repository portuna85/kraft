package com.kraft.recommend.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * POL-BALANCED 채점(03문서 4절). 각 기준은 강제 필터가 아닌 선호 점수이며 경계값을 포함한다.
 * 순수 함수 — 회원·게시글·HTTP·DB를 참조하지 않는다.
 */
public final class BalancedScorer {

    public record Evaluation(int score, List<ExplanationCode> codes) {
    }

    private BalancedScorer() {
    }

    public static Evaluation evaluate(List<Integer> sortedNumbers) {
        List<ExplanationCode> codes = new ArrayList<>();

        long oddCount = sortedNumbers.stream().filter(n -> n % 2 != 0).count();
        if (oddCount >= 2 && oddCount <= 4) {
            codes.add(ExplanationCode.ODD_EVEN_BALANCED);
        }

        long lowCount = sortedNumbers.stream().filter(n -> n <= 22).count();
        if (lowCount >= 2 && lowCount <= 4) {
            codes.add(ExplanationCode.LOW_HIGH_BALANCED);
        }

        int sum = sortedNumbers.stream().mapToInt(Integer::intValue).sum();
        if (sum >= 100 && sum <= 180) {
            codes.add(ExplanationCode.SUM_IN_RANGE);
        }

        int consecutivePairs = 0;
        for (int i = 1; i < sortedNumbers.size(); i++) {
            if (sortedNumbers.get(i) - sortedNumbers.get(i - 1) == 1) {
                consecutivePairs++;
            }
        }
        if (consecutivePairs <= 1) {
            codes.add(ExplanationCode.CONSECUTIVE_PAIR_LIMITED);
        }

        long decadesUsed = sortedNumbers.stream().map(BalancedScorer::decadeOf).distinct().count();
        if (decadesUsed >= 4) {
            codes.add(ExplanationCode.DECADE_SPREAD);
        }

        return new Evaluation(codes.size(), codes);
    }

    private static int decadeOf(int number) {
        if (number <= 10) {
            return 1;
        }
        if (number <= 20) {
            return 2;
        }
        if (number <= 30) {
            return 3;
        }
        if (number <= 40) {
            return 4;
        }
        return 5;
    }
}
