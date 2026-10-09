package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationFetchAttempt;
import com.kraft.recommend.domain.RecommendationFetchAttempt.Outcome;
import com.kraft.recommend.domain.RecommendationFetchAttempt.Trigger;
import com.kraft.recommend.domain.RecommendationFetchAttemptRepository;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.RecommendationImportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 동행복권에서 최신 회차를 받아 반영하는 한 번의 실행. 예약 실행({@link RecommendationAutoFetchScheduler})과
 * 관리자 화면의 "지금 수집"이 같은 코드를 쓴다. 한 번에 한 실행만 돈다 — 예약과 수동 실행이
 * 겹치거나 버튼이 연달아 눌려도 같은 회차를 두 번 받지 않는다.
 * <p>
 * {@link DhLotteryClient}의 조회 결과 중 {@code Unavailable}(전송 오류·봇 차단·검증 실패)은
 * {@code NotYetDrawn}과 구분해 WARN으로 남긴다 — 둘을 같은 로그로 묶으면 "이번 주는 원래
 * 조용히 넘어간 것"과 "매번 막히고 있어 사람이 봐야 하는 것"을 나중에 구분할 수 없다.
 * 시도마다 DB에 기록을 남겨(recommendation_fetch_attempts) 재시작해도 이력과 연속 실패 횟수가 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationFetchService {

    /**
     * 한 번의 실행에서 연속으로 따라잡는 최대 회차 수. 앱이 여러 주 내려가 있었다면 검증
     * 구간이 여러 회차만큼 뒤처질 수 있는데, 이 상한은 한 번에 과도한 요청을 보내지 않기 위한
     * 안전판이다.
     */
    static final int MAX_CATCHUP_ROUNDS = 10;

    /** 상태 복원에 되돌아볼 최근 시도 수. 연속 실패가 이보다 길면 이 값으로 잘려 보인다. */
    private static final int RESTORE_WINDOW = 50;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DhLotteryClient dhLotteryClient;
    private final RecommendationHistoryImporter importer;
    private final RecommendationHistoryStateRepository stateRepository;
    private final RecommendationFetchStatus fetchStatus;
    private final RecommendationFetchAttemptRepository attemptRepository;

    private final ReentrantLock running = new ReentrantLock();

    /** 한 실행의 결과. */
    public record RunResult(Status status, List<Integer> fetchedRounds, String message) {

        public enum Status {
            /** 정상 종료 — 받을 회차를 모두 받았거나 다음 회차가 아직 추첨 전이다. */
            DONE,
            /** 신뢰할 수 없는 응답이나 검증 실패로 중간에 멈췄다. */
            FAILED,
            /** 따라잡기 상한에 닿았다. 남은 것은 다음 실행이 이어받는다. */
            CATCHUP_LIMIT,
            /** 이미 다른 실행이 돌고 있어 시작하지 않았다. */
            BUSY
        }
    }

    /** 재시작 직후 DB의 시도 기록으로 마지막 성공·실패·연속 실패 횟수를 되돌린다. */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreStatus() {
        try {
            restoreFrom(attemptRepository.findAllByOrderByIdDesc(PageRequest.of(0, RESTORE_WINDOW)));
        } catch (RuntimeException e) {
            // 상태 복원 실패가 앱 기동을 막을 이유는 없다 — 연속 실패 횟수를 0에서 다시 센다.
            log.warn("수집 시도 기록으로 상태를 복원하지 못했습니다. 메모리 상태는 비어 있습니다.", e);
        }
    }

    /** package-private: 테스트가 DB 없이 복원 규칙만 확인한다. 최근 시도가 앞에 온다. */
    void restoreFrom(List<RecommendationFetchAttempt> newestFirst) {
        Instant lastSuccess = null;
        Instant lastFailure = null;
        String lastFailureReason = null;
        int consecutiveFailures = 0;
        boolean streakBroken = false;
        for (RecommendationFetchAttempt attempt : newestFirst) {
            Instant at = attempt.getAttemptedAt().atZone(KST).toInstant();
            if (attempt.getOutcome().isSuccessLike()) {
                streakBroken = true;
                if (lastSuccess == null) {
                    lastSuccess = at;
                }
            } else {
                if (lastFailure == null) {
                    lastFailure = at;
                    lastFailureReason = attempt.getDetail();
                }
                if (!streakBroken) {
                    consecutiveFailures++;
                }
            }
        }
        fetchStatus.restore(lastSuccess, lastFailure, lastFailureReason, consecutiveFailures);
    }

    /**
     * 다음에 받을 회차({@code verifiedThroughRound + 1})부터 아직 추첨 전이거나 실패할 때까지 이어서
     * 받는다. 이미 실행 중이면 아무것도 하지 않고 {@code BUSY}를 돌려준다.
     */
    public RunResult run(Trigger trigger) {
        if (!running.tryLock()) {
            log.info("수집이 이미 진행 중이라 이번 {} 실행은 건너뜁니다.", trigger);
            return new RunResult(RunResult.Status.BUSY, List.of(), "이미 다른 수집이 진행 중입니다.");
        }
        try {
            return doRun(trigger);
        } finally {
            running.unlock();
        }
    }

    private RunResult doRun(Trigger trigger) {
        List<Integer> fetched = new ArrayList<>();
        int target = currentVerifiedThroughRound() + 1;
        for (int attempt = 0; attempt < MAX_CATCHUP_ROUNDS; attempt++) {
            DhLotteryClient.FetchOutcome outcome = dhLotteryClient.fetchRound(target);
            switch (outcome) {
                case DhLotteryClient.FetchOutcome.Success success -> {
                    String failure = importDraw(success, target, trigger);
                    if (failure != null) {
                        // 검증 실패는 다음 실행이 이어받는다 — 이번 실행에서 더 진행하지 않는다.
                        return new RunResult(RunResult.Status.FAILED, fetched,
                                "회차 " + target + " 반영 검증 실패: " + failure);
                    }
                    fetched.add(target);
                    target++;
                }
                case DhLotteryClient.FetchOutcome.NotYetDrawn ignored -> {
                    log.info("회차 {}은(는) 아직 추첨 전으로 보입니다. 다음 예약 시각에 다시 시도합니다.", target);
                    record(trigger, Outcome.NOT_YET_DRAWN, target, null);
                    return new RunResult(RunResult.Status.DONE, fetched,
                            fetched.isEmpty() ? "회차 " + target + "은(는) 아직 추첨 전입니다."
                                    : fetched.size() + "개 회차를 반영했습니다. 회차 " + target + "은(는) 아직 추첨 전입니다.");
                }
                case DhLotteryClient.FetchOutcome.Unavailable unavailable -> {
                    log.warn("회차 {} 자동 수집 실패(신뢰할 수 없는 응답): {}. 다음 예약 시각에 다시 시도합니다.",
                            target, unavailable.reason());
                    record(trigger, Outcome.FAILED, target, unavailable.reason());
                    return new RunResult(RunResult.Status.FAILED, fetched,
                            "회차 " + target + " 조회 실패: " + unavailable.reason());
                }
            }
        }
        log.info("이번 실행의 따라잡기 상한({}회차)에 도달했습니다. 남은 backlog는 다음 예약 시각에 이어받습니다.",
                MAX_CATCHUP_ROUNDS);
        return new RunResult(RunResult.Status.CATCHUP_LIMIT, fetched,
                fetched.size() + "개 회차를 반영했습니다. 따라잡기 상한(" + MAX_CATCHUP_ROUNDS
                        + "회차)에 닿아 나머지는 다음 실행이 이어받습니다.");
    }

    private int currentVerifiedThroughRound() {
        return stateRepository.findById(1)
                .map(state -> state.getVerifiedThroughRound() == null ? 0 : state.getVerifiedThroughRound())
                .orElse(0);
    }

    /** @return 반영에 성공했으면 null, 검증 실패면 그 사유. */
    private String importDraw(DhLotteryClient.FetchOutcome.Success success, int target, Trigger trigger) {
        String source = trigger == Trigger.MANUAL ? "dhlottery-api-manual" : "dhlottery-api-auto";
        try {
            RecommendationHistoryImporter.Result result =
                    importer.importHistory(List.of(success.draw()), target, source);
            log.info("회차 {} 반영 완료(신규 {}건, 정정 {}건).", target, result.inserted(), result.updated());
            record(trigger, Outcome.FETCHED, target, null);
            return null;
        } catch (RecommendationImportException e) {
            log.warn("회차 {} 자동 반영 검증 실패 [{}]: {}. 다음 예약 시각에 다시 시도합니다.",
                    target, e.getReason(), e.getMessage());
            String reason = String.valueOf(e.getReason());
            record(trigger, Outcome.FAILED, target, reason);
            return reason;
        }
    }

    /** 메모리 상태(헬스 경보용)와 DB 기록(화면·재시작 복원용)에 함께 남긴다. 기록 실패가 수집을 막지는 않는다. */
    private void record(Trigger trigger, Outcome outcome, int round, String detail) {
        if (outcome.isSuccessLike()) {
            fetchStatus.recordSuccess();
        } else {
            fetchStatus.recordFailure(detail);
        }
        try {
            attemptRepository.save(RecommendationFetchAttempt.builder()
                    .attemptedAt(LocalDateTime.now(KST))
                    .trigger(trigger)
                    .outcome(outcome)
                    .roundNo(round)
                    .detail(detail)
                    .build());
        } catch (RuntimeException e) {
            log.warn("수집 시도 기록을 저장하지 못했습니다. round={}, outcome={}", round, outcome, e);
        }
    }
}
