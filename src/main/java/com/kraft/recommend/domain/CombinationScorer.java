package com.kraft.recommend.domain;

import java.util.List;

/**
 * POL-CHOICE(reduce_shared_winner_risk) 채점(03문서 4절). 조건은 중첩 적용된다(예: 35는
 * 32 이상 가점과 5·7 배수 감점이 모두 적용). 100~129 및 221 이상 합계에는 합계 가감점이 없다.
 * 순수 함수 — 회원·게시글·HTTP·DB를 참조하지 않는다.
 */
public final class CombinationScorer {

    private CombinationScorer() {
    }

    public static int score(List<Integer> sortedNumbers) {
        int score = 0;
        for (int number : sortedNumbers) {
            if (number >= 32 && number <= 45) {
                score += 3;
            }
            if (number >= 1 && number <= 9) {
                score -= 4;
            }
            if (number % 5 == 0) {
                score -= 2;
            }
            if (number % 7 == 0) {
                score -= 1;
            }
        }

        int sum = sortedNumbers.stream().mapToInt(Integer::intValue).sum();
        if (sum < 100) {
            score -= 5;
        } else if (sum >= 130 && sum <= 220) {
            score += 4;
        }

        for (int i = 1; i < sortedNumbers.size(); i++) {
            if (sortedNumbers.get(i) - sortedNumbers.get(i - 1) == 1) {
                score -= 1;
            }
        }

        return score;
    }
}
