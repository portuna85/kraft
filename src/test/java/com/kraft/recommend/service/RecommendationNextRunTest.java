package com.kraft.recommend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 관리자 화면이 보여주는 "다음 예약 수집" 시각. 토 21:30·22:00, 일 06:00·07:00(KST) 중 가장 가까운 것. */
class RecommendationNextRunTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static ZonedDateTime at(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, KST);
    }

    @Test
    @DisplayName("평일에는 이번 토요일 21:30이다")
    void weekday_nextIsSaturday2130() {
        // 2026-10-04는 일요일 — 07:00이 지났으니 다음은 10-10(토) 21:30
        assertThat(RecommendationAutoFetchScheduler.nextRun(at(10, 5, 12, 0))).isEqualTo(at(10, 10, 21, 30));
    }

    @Test
    @DisplayName("토요일 21:30 직후에는 22:00이다")
    void afterFirstSlot_nextIsSaturday2200() {
        assertThat(RecommendationAutoFetchScheduler.nextRun(at(10, 10, 21, 31))).isEqualTo(at(10, 10, 22, 0));
    }

    @Test
    @DisplayName("토요일 22:00 직후에는 일요일 06:00이다")
    void afterSaturdayNight_nextIsSunday0600() {
        assertThat(RecommendationAutoFetchScheduler.nextRun(at(10, 10, 22, 1))).isEqualTo(at(10, 11, 6, 0));
    }

    @Test
    @DisplayName("일요일 07:00 직후에는 다음 주 토요일 21:30이다")
    void afterSunday0700_nextIsNextSaturday() {
        assertThat(RecommendationAutoFetchScheduler.nextRun(at(10, 11, 7, 1))).isEqualTo(at(10, 17, 21, 30));
    }

    @Test
    @DisplayName("다른 시간대로 넘겨도 KST 기준으로 계산한다")
    void otherZone_isConvertedToKst() {
        ZonedDateTime utc = at(10, 10, 21, 31).withZoneSameInstant(ZoneId.of("UTC"));

        assertThat(RecommendationAutoFetchScheduler.nextRun(utc).toInstant()).isEqualTo(at(10, 10, 22, 0).toInstant());
    }
}
