package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationFetchAttempt.Trigger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 매주 로또 추첨(토요일 20:35 KST) 이후 최신 회차를 동행복권에서 받아와 반영한다(사용자
 * 결정으로 {@code docs/02} §5.3의 "자동 수집을 만들지 않는다" 원칙을 뒤집은 것 — 운영
 * 런북에 근거를 남긴다). 추첨 직후 결과 게시가 지연될 수 있어 토요일 21:30·22:00, 일요일
 * 06:00·07:00 KST 네 번 시도한다 — 앞선 시도가 이미 성공했으면 뒤 시도는 "다음 회차는 아직
 * 추첨 전"이라는 응답만 받고 조용히 끝난다(자연스러운 재시도).
 * <p>
 * 실제 수집은 {@link RecommendationFetchService}가 한다 — 관리자 화면의 "지금 수집"과 같은 코드다.
 */
@RequiredArgsConstructor
@Component
public class RecommendationAutoFetchScheduler {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static final String SAT_2130 = "0 30 21 * * SAT";
    private static final String SAT_2200 = "0 0 22 * * SAT";
    private static final String SUN_0600 = "0 0 6 * * SUN";
    private static final String SUN_0700 = "0 0 7 * * SUN";

    private static final List<CronExpression> SCHEDULE = List.of(
            CronExpression.parse(SAT_2130), CronExpression.parse(SAT_2200),
            CronExpression.parse(SUN_0600), CronExpression.parse(SUN_0700));

    private final RecommendationFetchService fetchService;

    @Value("${app.recommend.auto-fetch.enabled:true}")
    private boolean enabled;

    /** 지금 이후 가장 가까운 예약 시각(KST). 자동 수집이 꺼져 있어도 달력상의 시각을 돌려준다. */
    public static ZonedDateTime nextRun(ZonedDateTime from) {
        ZonedDateTime base = from.withZoneSameInstant(ZONE);
        return SCHEDULE.stream()
                .map(cron -> cron.next(base))
                .min(ZonedDateTime::compareTo)
                .orElseThrow();
    }

    @Scheduled(cron = SAT_2130, zone = "Asia/Seoul")
    @Scheduled(cron = SAT_2200, zone = "Asia/Seoul")
    @Scheduled(cron = SUN_0600, zone = "Asia/Seoul")
    @Scheduled(cron = SUN_0700, zone = "Asia/Seoul")
    public void fetchLatestIfDue() {
        if (!enabled) {
            return;
        }
        fetchService.run(Trigger.SCHEDULED);
    }
}
