package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.RecommendationImportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 매주 로또 추첨(토요일 20:35 KST) 이후 최신 회차를 동행복권에서 받아와 반영한다(사용자
 * 결정으로 {@code docs/02} §5.3의 "자동 수집을 만들지 않는다" 원칙을 뒤집은 것 — 운영
 * 런북에 근거를 남긴다). 추첨 직후 결과 게시가 지연될 수 있어 토요일 21:30·22:00, 일요일
 * 06:00·07:00 KST 네 번 시도한다 — 앞선 시도가 이미 성공했으면 뒤 시도는 "다음 회차는 아직
 * 추첨 전"이라는 응답만 받고 조용히 끝난다(자연스러운 재시도).
 * <p>
 * {@link DhLotteryClient}의 조회 결과 중 {@code Unavailable}(전송 오류·봇 차단·검증 실패)은
 * {@code NotYetDrawn}과 구분해 WARN으로 남긴다 — 둘을 같은 로그로 묶으면 "이번 주는 원래
 * 조용히 넘어간 것"과 "매번 막히고 있어 사람이 봐야 하는 것"을 나중에 구분할 수 없다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RecommendationAutoFetchScheduler {

    private final DhLotteryClient dhLotteryClient;
    private final RecommendationHistoryImporter importer;
    private final RecommendationHistoryStateRepository stateRepository;

    @Value("${app.recommend.auto-fetch.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "0 30 21 * * SAT", zone = "Asia/Seoul")
    @Scheduled(cron = "0 0 22 * * SAT", zone = "Asia/Seoul")
    @Scheduled(cron = "0 0 6 * * SUN", zone = "Asia/Seoul")
    @Scheduled(cron = "0 0 7 * * SUN", zone = "Asia/Seoul")
    public void fetchLatestIfDue() {
        if (!enabled) {
            return;
        }

        int verifiedThroughRound = stateRepository.findById(1)
                .map(state -> state.getVerifiedThroughRound() == null ? 0 : state.getVerifiedThroughRound())
                .orElse(0);
        int target = verifiedThroughRound + 1;

        DhLotteryClient.FetchOutcome outcome = dhLotteryClient.fetchRound(target);
        switch (outcome) {
            case DhLotteryClient.FetchOutcome.Success success -> importDraw(success, target);
            case DhLotteryClient.FetchOutcome.NotYetDrawn ignored ->
                    log.info("회차 {}은(는) 아직 추첨 전으로 보입니다. 다음 예약 시각에 다시 시도합니다.", target);
            case DhLotteryClient.FetchOutcome.Unavailable unavailable ->
                    log.warn("회차 {} 자동 수집 실패(신뢰할 수 없는 응답): {}. 다음 예약 시각에 다시 시도합니다.",
                            target, unavailable.reason());
        }
    }

    private void importDraw(DhLotteryClient.FetchOutcome.Success success, int target) {
        try {
            RecommendationHistoryImporter.Result result =
                    importer.importHistory(List.of(success.draw()), target, "dhlottery-api-auto");
            log.info("회차 {} 자동 반영 완료(신규 {}건, 정정 {}건).",
                    target, result.inserted(), result.updated());
        } catch (RecommendationImportException e) {
            log.warn("회차 {} 자동 반영 검증 실패 [{}]: {}. 다음 예약 시각에 다시 시도합니다.",
                    target, e.getReason(), e.getMessage());
        }
    }
}
