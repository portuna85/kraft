package com.kraft.recommend;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 조합의 "형태 균형" 정도를 평가하는 소프트 점수기 — 버전 {@link #VERSION}.
 *
 * 당첨 확률을 높이는 목적이 아니라, 과거 분포와 형태를 참고해 한쪽으로 치우치지 않은
 * 조합을 선호하도록 정렬하는 데만 쓰인다(문서 9.1절 BALANCED). 조건은 절대 필터가
 * 아니라 가점이며, 만족한 조건마다 {@link ExplanationCode}를 함께 반환해 프런트가
 * 근거를 표시할 수 있게 한다.
 */
@Component
public class BalancedScorer {

    public static final String VERSION = "balanced-v1";

    public BalancedEvaluation evaluate(List<Integer> sorted) {
        int score = 0;
        List<ExplanationCode> codes = new ArrayList<>();

        int oddCount = (int) sorted.stream().filter(n -> n % 2 != 0).count();
        if (oddCount >= 2 && oddCount <= 4) {
            score++;
            codes.add(ExplanationCode.ODD_EVEN_BALANCED);
        }

        int lowCount = (int) sorted.stream().filter(n -> n <= 22).count();
        if (lowCount >= 2 && lowCount <= 4) {
            score++;
            codes.add(ExplanationCode.LOW_HIGH_BALANCED);
        }

        int sum = sorted.stream().mapToInt(Integer::intValue).sum();
        if (sum >= 100 && sum <= 180) {
            score++;
            codes.add(ExplanationCode.SUM_IN_RANGE);
        }

        int consecutivePairs = 0;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) - sorted.get(i - 1) == 1) {
                consecutivePairs++;
            }
        }
        if (consecutivePairs <= 1) {
            score++;
            codes.add(ExplanationCode.CONSECUTIVE_PAIR_LIMITED);
        }

        long decadesUsed = sorted.stream().map(BalancedScorer::decadeOf).distinct().count();
        if (decadesUsed >= 4) {
            score++;
            codes.add(ExplanationCode.DECADE_SPREAD);
        }

        return new BalancedEvaluation(score, codes);
    }

    private static int decadeOf(int number) {
        if (number <= 10) {
            return 0;
        }
        if (number <= 20) {
            return 1;
        }
        if (number <= 30) {
            return 2;
        }
        if (number <= 40) {
            return 3;
        }
        return 4;
    }
}
