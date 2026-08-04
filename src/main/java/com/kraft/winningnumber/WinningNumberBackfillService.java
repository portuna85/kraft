package com.kraft.winningnumber;

import com.kraft.common.error.ApiException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 최신 회차를 모르는 상태에서 전체 회차를 수집(백필)하는 로직.
 *
 * <p>마지막 저장 회차 다음(빈 DB면 1회차)부터 시작해, 외부 API가 "해당 회차 없음"을
 * 응답할 때까지 회차를 1씩 올려가며 순차 수집한다. 즉 최신 회차 번호를 사전에 알 필요가 없다.</p>
 *
 * <p>설계 포인트:</p>
 * <ul>
 *   <li>회차별로 {@link WinningNumberCommandService#upsertWithResult}를 호출 — 각 회차가
 *       독립 트랜잭션으로 커밋되어, 중간 실패가 이미 수집한 회차를 롤백하지 않는다.</li>
 *   <li>회차마다 이벤트를 발행하지 않고 완료 후 1회만 발행 — ISR 재검증 웹훅·추천 캐시
 *       재적재가 수천 번 폭주하는 것을 방지한다.</li>
 *   <li>정상 응답이지만 데이터가 없는 회차(=최신 도달)는 종료, 일시 오류는 재시도,
 *       설정 오류 등 치명적 오류는 즉시 중단한다.</li>
 * </ul>
 */
@Service
public class WinningNumberBackfillService {

    private static final Logger log = LoggerFactory.getLogger(WinningNumberBackfillService.class);

    /** 정상 응답이나 해당 회차 데이터가 없음 → 최신 회차 도달로 보고 정상 종료. */
    private static final Set<String> END_OF_DATA_CODES =
            Set.of("LOTTO_SOURCE_ROUND_NOT_FOUND");
    /** 재시도가 무의미한 치명적 오류 → 즉시 중단. */
    private static final Set<String> FATAL_CODES =
            Set.of("LOTTO_SOURCE_DISABLED", "LOTTO_SOURCE_VALIDATION_ERROR",
                    "LOTTO_SOURCE_ROUND_MISMATCH", "LOTTO_SOURCE_INVALID_DATE");
    /** 무한 루프 방지 상한 (실제 6/45 회차 수보다 충분히 큼). */
    private static final int SAFETY_CAP = 10_000;

    private final WinningNumberRepository winningNumberRepository;
    private final ExternalWinningNumberFetchClient fetchClient;
    private final WinningNumberCommandService commandService;
    private final WinningNumberCollectionService collectionService;

    private final long delayMs;
    private final long retryBaseMs;
    private final int maxRetriesPerRound;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public WinningNumberBackfillService(
            WinningNumberRepository winningNumberRepository,
            ExternalWinningNumberFetchClient fetchClient,
            WinningNumberCommandService commandService,
            WinningNumberCollectionService collectionService,
            @Value("${kraft.external-lotto.backfill-delay-ms:200}") long delayMs,
            @Value("${kraft.external-lotto.backfill-retry-base-ms:1000}") long retryBaseMs,
            @Value("${kraft.external-lotto.backfill-max-retries:3}") int maxRetriesPerRound) {
        this.winningNumberRepository = winningNumberRepository;
        this.fetchClient = fetchClient;
        this.commandService = commandService;
        this.collectionService = collectionService;
        this.delayMs = delayMs;
        this.retryBaseMs = retryBaseMs;
        this.maxRetriesPerRound = maxRetriesPerRound;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 시작 예약을 시도한다. 성공(true)한 호출자만 {@link #backfillAllAsync()}를 호출해야 한다.
     * 이 메서드를 {@code isRunning()} 체크와 {@code backfillAllAsync()} 호출 사이의 별도 단계로
     * 두지 않고 동기 CAS 하나로 합쳐, 두 호출 사이의 TOCTOU 구간(동시 요청이 둘 다 "실행 중 아님"을
     * 보고 둘 다 진행하는 경우)을 없앤다.
     */
    public boolean tryStart() {
        return running.compareAndSet(false, true);
    }

    /** tryStart()가 성공적으로 시작 예약을 했지만 태스크 제출 자체가 거부된 경우 호출자가 되돌리는 용도. */
    public void releaseStart() {
        running.set(false);
    }

    /** 관리자 트리거용 — 백그라운드 실행. tryStart() 성공 후에만 호출해야 한다. */
    @Async("backfillTaskExecutor")
    public void backfillAllAsync() {
        try {
            backfillAll();
        } catch (RuntimeException e) {
            log.error("전체 회차 수집 중 예기치 못한 오류", e);
        } finally {
            running.set(false);
        }
    }

    /** 동기 실행 — 1회차(또는 마지막 저장 회차 다음)부터 최신 회차까지 순차 수집. */
    BackfillResult backfillAll() {
        int startRound = firstMissingRound();
        log.info("전체 회차 수집 시작: startRound={}", startRound);

        int round = startRound;
        int collected = 0;
        int updated = 0;
        int failStreak = 0;
        Integer lastCollected = null;
        String stopReason;

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                stopReason = "인터럽트로 중단 (round=%d)".formatted(round);
                break;
            }
            if (round - startRound >= SAFETY_CAP) {
                stopReason = "안전 상한(%d) 도달로 중단 (round=%d)".formatted(SAFETY_CAP, round);
                break;
            }
            try {
                WinningNumberUpsertRequest request = fetchClient.fetchRound(round);
                WinningNumberUpsertResult result = commandService.upsertWithResult(request);
                collected++;
                if (result.changed()) {
                    updated++;
                }
                lastCollected = round;
                failStreak = 0;
                round++;
                SleepUtils.sleepQuietly(delayMs);
            } catch (RuntimeException ex) {
                Outcome outcome = classify(ex);
                if (outcome == Outcome.END_OF_DATA) {
                    stopReason = "%d회차 없음 — 최신 회차까지 수집 완료".formatted(round);
                    break;
                }
                if (outcome == Outcome.FATAL) {
                    stopReason = "치명적 오류로 중단 (round=%d): %s".formatted(round, ex.getMessage());
                    break;
                }
                if (++failStreak > maxRetriesPerRound) {
                    stopReason = "%d회차 %d회 연속 실패로 중단: %s".formatted(round, failStreak, ex.getMessage());
                    break;
                }
                log.warn("{}회차 수집 일시 실패 ({}/{}) — 재시도: {}",
                        round, failStreak, maxRetriesPerRound, ex.getMessage());
                SleepUtils.sleepQuietly(retryBaseMs * failStreak);
            }
        }

        if (collected > 0 && lastCollected != null) {
            // 회차당이 아닌 완료 후 1회만 이벤트 발행 → ISR 재검증·추천 캐시 재적재.
            collectionService.publishBulkCollected(lastCollected);
        }

        BackfillResult result = new BackfillResult(startRound, lastCollected, collected, updated, stopReason);
        log.info("전체 회차 수집 종료: startRound={} lastCollected={} collected={} updated={} reason='{}'",
                result.startRound(), result.lastCollectedRound(), result.collectedCount(),
                result.updatedCount(), result.stopReason());
        return result;
    }

    private enum Outcome { END_OF_DATA, FATAL, TRANSIENT }

    private Outcome classify(RuntimeException ex) {
        if (ex instanceof ApiException api) {
            if (END_OF_DATA_CODES.contains(api.getCode())) {
                return Outcome.END_OF_DATA;
            }
            if (FATAL_CODES.contains(api.getCode())) {
                return Outcome.FATAL;
            }
        }
        return Outcome.TRANSIENT;
    }


    private int firstMissingRound() {
        List<Integer> storedRounds = winningNumberRepository.findAllRoundsOrderByRoundAsc();
        int expected = 1;
        for (Integer storedRound : storedRounds) {
            if (storedRound == null || storedRound < expected) {
                continue;
            }
            if (storedRound > expected) {
                break;
            }
            expected++;
        }
        return expected;
    }
}
