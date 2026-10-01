package com.kraft.home.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 과거 당첨 번호를 있는 그대로 세어 보여주는 요약. 다음 추첨에 대한 어떤 신호도 아니다 —
 * 화면 문구도 "과거 기록"으로만 쓴다. 보너스 번호는 세지 않는다(당첨번호 6개 기준).
 *
 * @param window    집계한 최근 회차 수(이력이 짧으면 그만큼)
 * @param hot       최근 {@code window}회에서 많이 나온 번호
 * @param overdue   마지막 출현 이후 가장 오래 지난 번호(전체 이력 기준, 값은 지난 회차 수)
 * @param odd       최근 구간 당첨번호 중 홀수 개수
 * @param even      같은 구간의 짝수 개수
 * @param low       같은 구간의 1~22 개수
 * @param high      같은 구간의 23~45 개수
 * @param bands     같은 구간의 번호대별 개수(1~10, 11~20, 21~30, 31~40, 41~45)
 */
public record DrawInsights(int window, List<NumberStat> hot, List<NumberStat> overdue,
                           int odd, int even, int low, int high, List<Integer> bands) {

    public static final int MAX_NUMBER = 45;
    public static final int LOW_MAX = 22;
    private static final int TOP = 5;
    private static final int[] BAND_ENDS = {10, 20, 30, 40, 45};

    /** 번호와 그에 붙은 수치(출현 횟수 또는 지난 회차 수). */
    public record NumberStat(int number, int value) {
    }

    /** @param newestFirst 최신 회차부터 나열한 당첨번호들(회차마다 6개) */
    public static DrawInsights of(List<List<Integer>> newestFirst, int windowSize) {
        int window = Math.min(windowSize, newestFirst.size());
        int[] freq = new int[MAX_NUMBER + 1];
        int[] gap = new int[MAX_NUMBER + 1];
        java.util.Arrays.fill(gap, newestFirst.size());
        int odd = 0;
        int low = 0;
        int[] bands = new int[BAND_ENDS.length];
        for (int i = 0; i < newestFirst.size(); i++) {
            for (int n : newestFirst.get(i)) {
                if (gap[n] == newestFirst.size()) {
                    gap[n] = i;
                }
                if (i < window) {
                    freq[n]++;
                    odd += n % 2;
                    low += n <= LOW_MAX ? 1 : 0;
                    bands[bandOf(n)]++;
                }
            }
        }
        int total = window * 6;
        return new DrawInsights(window, top(freq, true), top(gap, false),
                odd, total - odd, low, total - low, IntStream.of(bands).boxed().toList());
    }

    private static int bandOf(int n) {
        int b = 0;
        while (n > BAND_ENDS[b]) {
            b++;
        }
        return b;
    }

    /** 값이 큰 순(동률은 작은 번호 먼저)으로 상위 5개. {@code skipZero}면 0인 번호는 뺀다. */
    private static List<NumberStat> top(int[] values, boolean skipZero) {
        return IntStream.rangeClosed(1, MAX_NUMBER)
                .filter(n -> !skipZero || values[n] > 0)
                .mapToObj(n -> new NumberStat(n, values[n]))
                .sorted(Comparator.comparingInt(NumberStat::value).reversed()
                        .thenComparingInt(NumberStat::number))
                .limit(TOP)
                .toList();
    }

    public boolean isEmpty() {
        return window == 0;
    }
}
