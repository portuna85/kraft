package com.kraft.winningnumber;

import com.kraft.common.error.ApiException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * class-level {@code @Transactional}을 두지 않는다. {@link #fetchRound}로 외부 HTTP 호출이
 * DB 트랜잭션 밖에서 일어나야 커넥션을 장시간 점유하지 않는다(P1-3). 실제 쓰기는
 * {@link WinningNumberCommandService}(자체 {@code @Transactional} 빈)와
 * {@link WinningNumberOperationLogService}(REQUIRES_NEW)가 호출당 짧은 트랜잭션으로 처리한다.
 */
@Service
public class WinningNumberCollectionService {

    private static final Logger log = LoggerFactory.getLogger(WinningNumberCollectionService.class);

    private final WinningNumberRepository winningNumberRepository;
    private final ExternalWinningNumberFetchClient externalWinningNumberFetchClient;
    private final WinningNumberCommandService winningNumberCommandService;
    private final ApplicationEventPublisher eventPublisher;
    private final WinningNumberCollectionEventPublisher collectionEventPublisher;
    private final Counter collectFailureCounter;
    private final long catchUpDelayMs;

    public WinningNumberCollectionService(WinningNumberRepository winningNumberRepository,
                                          ExternalWinningNumberFetchClient externalWinningNumberFetchClient,
                                          WinningNumberCommandService winningNumberCommandService,
                                          ApplicationEventPublisher eventPublisher,
                                          WinningNumberCollectionEventPublisher collectionEventPublisher,
                                          MeterRegistry meterRegistry,
                                          @Value("${kraft.external-lotto.catch-up-delay-ms:200}") long catchUpDelayMs) {
        this.winningNumberRepository = winningNumberRepository;
        this.externalWinningNumberFetchClient = externalWinningNumberFetchClient;
        this.winningNumberCommandService = winningNumberCommandService;
        this.eventPublisher = eventPublisher;
        this.collectionEventPublisher = collectionEventPublisher;
        this.collectFailureCounter = Counter.builder("kraft_lotto_external_collect_failures_total")
                .description("외부 회차 수집 실패 횟수(end-of-data 제외)")
                .register(meterRegistry);
        this.catchUpDelayMs = catchUpDelayMs;
    }

    // KB-17: operationlog를 직접 호출하는 대신 이벤트를 발행한다(winningnumber→operationlog
    // 순환의 한 변을 역전) — WinningNumberCollectionLoggedEvent 참고.
    private void logSuccess(Integer round, String sourceDetail, String message) {
        eventPublisher.publishEvent(new WinningNumberCollectionLoggedEvent(
                WinningNumberOperationType.EXTERNAL_COLLECT, true, round, sourceDetail, message));
    }

    private void logFailure(Integer round, String sourceDetail, String message) {
        eventPublisher.publishEvent(new WinningNumberCollectionLoggedEvent(
                WinningNumberOperationType.EXTERNAL_COLLECT, false, round, sourceDetail, message));
    }

    /**
     * 전체 수집(백필) 완료 후 1회만 호출한다. 회차당 이벤트 폭주를 피하기 위함이다.
     */
    public void publishBulkCollected(int latestRound) {
        log.info("전체 수집 완료 이벤트 발행: latestRound={}", latestRound);
        collectionEventPublisher.publish(new WinningNumbersCollectedEvent(latestRound, true));
    }

    public WinningNumberResponse collectLatest() {
        Optional<WinningNumber> latest = winningNumberRepository.findTopByOrderByRoundDesc();
        int nextRound = latest.map(winningNumber -> winningNumber.getRound() + 1).orElse(1);
        log.info("최신 회차 수집 시작: nextRound={}", nextRound);
        try {
            return collectFetchedRound(nextRound);
        } catch (RuntimeException exception) {
            if (isEndOfData(exception) && latest.isPresent()) {
                WinningNumber latestRound = latest.get();
                logSuccess(
                        latestRound.getRound(),
                        "external-source",
                        "다음 회차가 아직 공개되지 않았습니다. 이미 최신 회차까지 수집되어 있습니다."
                );
                log.info("최신 회차 수집 생략: latestRound={} nextRound={} reason={}",
                        latestRound.getRound(), nextRound, exception.getMessage());
                return WinningNumberResponse.from(latestRound);
            }
            collectFailureCounter.increment();
            logFailure(nextRound, "external-source", exception.getMessage());
            throw exception;
        }
    }

    /**
     * 1회 스케줄에서 최대 maxRounds 회차까지 순차 수집한다. 자동 수집이 회차당 1건만
     * 회복하던 기존 동작은 일시적 장애로 1주 이상 누락이 쌓이면 회복이 지연됐다(P0).
     */
    public List<WinningNumberResponse> collectUpToLatest(int maxRounds) {
        List<WinningNumberResponse> collected = new ArrayList<>();
        Optional<WinningNumber> latestBefore = winningNumberRepository.findTopByOrderByRoundDesc();
        int nextRound = latestBefore.map(winningNumber -> winningNumber.getRound() + 1).orElse(1);
        boolean hadPreviousData = latestBefore.isPresent();
        for (int i = 0; i < maxRounds; i++) {
            try {
                collected.add(collectFetchedRoundResult(nextRound).response());
                nextRound++;
                if (i < maxRounds - 1) {
                    SleepUtils.sleepQuietly(catchUpDelayMs);
                }
            } catch (RuntimeException exception) {
                if (isEndOfData(exception) && hadPreviousData) {
                    log.info("자동 수집 catch-up 종료: latestRound={} 추가 회차 없음", nextRound - 1);
                    break;
                }
                collectFailureCounter.increment();
                logFailure(nextRound, "external-source", exception.getMessage());
                throw exception;
            }
        }
        if (!collected.isEmpty()) {
            // 회차당이 아닌 catch-up 종료 후 1회만 발행 — 통계 재집계·추천 캐시 재적재·ISR 웹훅 중복 방지.
            publishBulkCollected(collected.get(collected.size() - 1).round());
        }
        return collected;
    }

    public WinningNumberResponse collectRound(int round) {
        log.info("회차 수집 시작: round={}", round);
        try {
            return collectFetchedRound(round);
        } catch (RuntimeException exception) {
            collectFailureCounter.increment();
            logFailure(round, "external-source", exception.getMessage());
            throw exception;
        }
    }

    private WinningNumberResponse collectFetchedRound(int round) {
        WinningNumberUpsertResult result = collectFetchedRoundResult(round);
        collectionEventPublisher.publish(new WinningNumbersCollectedEvent(result.response().round(), result.changed()));
        return result.response();
    }

    private WinningNumberUpsertResult collectFetchedRoundResult(int round) {
        WinningNumberUpsertRequest request = externalWinningNumberFetchClient.fetchRound(round);
        WinningNumberUpsertResult result = winningNumberCommandService.upsertWithResult(request);
        WinningNumberResponse response = result.response();
        logSuccess(response.round(), "external-source", "외부 회차 수집 및 저장에 성공했습니다.");
        log.info("회차 수집 완료: round={} drawDate={} changed={}", response.round(), response.drawDate(), result.changed());
        return result;
    }

    private boolean isEndOfData(RuntimeException exception) {
        return exception instanceof ApiException apiException
                && "LOTTO_SOURCE_ROUND_NOT_FOUND".equals(apiException.getCode());
    }

}
