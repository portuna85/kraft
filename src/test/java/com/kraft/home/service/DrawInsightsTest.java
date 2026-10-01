package com.kraft.home.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DrawInsightsTest {

    private static final List<Integer> A = List.of(1, 2, 3, 4, 5, 6);
    private static final List<Integer> B = List.of(1, 11, 21, 31, 41, 45);

    @Test
    @DisplayName("이력이 없으면 비어 있다")
    void empty() {
        DrawInsights r = DrawInsights.of(List.of(), 30);
        assertThat(r.isEmpty()).isTrue();
        assertThat(r.hot()).isEmpty();
        assertThat(r.bands()).containsExactly(0, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("최근 구간의 출현 횟수는 동률이면 작은 번호가 먼저다")
    void hotOrdering() {
        // 최신: B, 이전: A → 1은 2회, 나머지는 1회
        DrawInsights r = DrawInsights.of(List.of(B, A), 30);
        assertThat(r.hot()).extracting(DrawInsights.NumberStat::number).containsExactly(1, 2, 3, 4, 5);
        assertThat(r.hot().get(0).value()).isEqualTo(2);
    }

    @Test
    @DisplayName("미출현 기간은 최신 회차를 0으로 센 지난 회차 수이고, 한 번도 안 나온 번호가 가장 길다")
    void overdue() {
        DrawInsights r = DrawInsights.of(List.of(B, A), 30);
        // 한 번도 안 나온 번호(7)는 이력 길이(2)만큼, 최신에 나온 번호는 0
        assertThat(r.overdue().get(0)).isEqualTo(new DrawInsights.NumberStat(7, 2));
        assertThat(r.overdue()).allMatch(s -> s.value() == 2);
    }

    @Test
    @DisplayName("홀짝·고저·번호대 분포는 창 안의 번호만 센다")
    void distributionsUseWindowOnly() {
        // 창 1 → 최신(B)만: 홀수 1,11,21,31,41,45 = 6개, 저(<=22) 1,11,21 = 3개
        DrawInsights r = DrawInsights.of(List.of(B, A), 1);
        assertThat(r.window()).isEqualTo(1);
        assertThat(r.odd()).isEqualTo(6);
        assertThat(r.even()).isZero();
        assertThat(r.low()).isEqualTo(3);
        assertThat(r.high()).isEqualTo(3);
        assertThat(r.bands()).containsExactly(1, 1, 1, 1, 2);
    }
}
