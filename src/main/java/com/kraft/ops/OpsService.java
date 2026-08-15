package com.kraft.ops;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.operationlog.WinningNumberManualUpsertEvent;
import com.kraft.operationlog.WinningNumberOperationLogFilter;
import com.kraft.operationlog.WinningNumberOperationLogResponse;
import com.kraft.operationlog.WinningNumberOperationLogService;
import com.kraft.operationlog.WinningNumberOperationStatus;
import com.kraft.winningnumber.LottoDrawScheduleCalculator;
import com.kraft.winningnumber.WinningNumberCollectionService;
import com.kraft.winningnumber.WinningNumberCommandService;
import com.kraft.winningnumber.WinningNumberOperationType;
import com.kraft.winningnumber.WinningNumberRepository;
import com.kraft.winningnumber.WinningNumberResponse;
import com.kraft.winningnumber.WinningNumberUpsertRequest;
import com.kraft.winningnumber.WinningNumberUpsertResult;
import com.kraft.winningnumber.WinningNumbersCollectedEvent;
import com.kraft.common.web.PageResponse;
import com.kraft.common.web.RequestIdFilter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// class-level @Transactional(readOnly = true)를 두지 않는다. 예전에는 클래스 레벨에 있었는데,
// collectLatestWinningNumber()/collectWinningNumber()에 메서드 애너테이션을 생략하는 것만으로는
// "트랜잭션 밖에서 실행"이 되지 않는다 — Spring은 메서드 애너테이션이 없으면 클래스 레벨을
// 그대로 상속하므로, 외부 HTTP 수집이 읽기 전용 트랜잭션 안에서 실행돼 DB 커넥션을 붙들고,
// 그 안에서 호출되는 WinningNumberCommandService의 쓰기(REQUIRED)가 읽기 전용 트랜잭션에
// 참여해 dirty checking/flush가 스킵될 위험이 있었다(B1). 조회 메서드에만 개별로 붙인다.
@Service
public class OpsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(OpsService.class);

    private final WinningNumberRepository winningNumberRepository;
    private final WinningNumberCommandService winningNumberCommandService;
    private final WinningNumberCollectionService winningNumberCollectionService;
    private final WinningNumberOperationLogService winningNumberOperationLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final LottoDrawScheduleCalculator drawScheduleCalculator;

    public OpsService(WinningNumberRepository winningNumberRepository,
                      WinningNumberCommandService winningNumberCommandService,
                      WinningNumberCollectionService winningNumberCollectionService,
                      WinningNumberOperationLogService winningNumberOperationLogService,
                      ApplicationEventPublisher eventPublisher,
                      Clock clock,
                      LottoDrawScheduleCalculator drawScheduleCalculator) {
        this.winningNumberRepository = winningNumberRepository;
        this.winningNumberCommandService = winningNumberCommandService;
        this.winningNumberCollectionService = winningNumberCollectionService;
        this.winningNumberOperationLogService = winningNumberOperationLogService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.drawScheduleCalculator = drawScheduleCalculator;
    }

    @Transactional(readOnly = true)
    public OpsSummaryResponse getSummary() {

        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(KST);
        OpsSummaryResponse response = winningNumberRepository.findTopByOrderByRoundDesc()
                .map(latest -> new OpsSummaryResponse(
                        "kraft-lotto",
                        KST.getId(),
                        "정상",
                        latest.getRound(),
                        latest.getDrawDate().toString(),
                        now,
                        drawScheduleCalculator.isFresh(latest.getDrawDate(), now)
                ))
                .orElseGet(() -> new OpsSummaryResponse(
                        "kraft-lotto",
                        KST.getId(),
                        "데이터 없음",
                        null,
                        null,
                        now,
                        false
                ));
        log.info("운영 상태 조회 완료: latestRound={} fresh={}", response.latestRound(), response.fresh());
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<WinningNumberOperationLogResponse> getRecentOperationLogs(int page,
                                                                                 int size,
                                                                                 String operationType,
                                                                                 String executionStatus,
                                                                                 Integer round,
                                                                                 String from,
                                                                                 String to) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.clamp(size, 1, 100);
        WinningNumberOperationLogFilter filter = new WinningNumberOperationLogFilter(
                parseOperationType(operationType),
                parseExecutionStatus(executionStatus),
                round,
                parseFromDate(from),
                parseToDateExclusive(to)
        );
        log.info("운영 작업 로그 조회: page={} size={} operationType={} executionStatus={} round={} from={} toExclusive={}",
                normalizedPage,
                normalizedSize,
                filter.operationType(),
                filter.executionStatus(),
                filter.round(),
                filter.createdFrom(),
                filter.createdToExclusive());
        return winningNumberOperationLogService.getRecentLogs(normalizedPage, normalizedSize, filter);
    }

    @Transactional
    public WinningNumberResponse upsertWinningNumber(WinningNumberUpsertRequest request) {
        String caller = callerDetail();
        log.info("운영 수동 회차 저장 시작: round={} drawDate={} caller={}", request.round(), request.drawDate(), caller);
        try {
            WinningNumberUpsertResult result = winningNumberCommandService.upsertWithResult(request);
            WinningNumberResponse response = result.response();
            if (result.changed()) {
                // 수동 보정도 자동 수집과 동일하게 이벤트를 발행해야 통계 재집계·추천 캐시·ETag·ISR이
                // 갱신된다. REQUIRES_NEW publisher를 쓰면 이 메서드의 트랜잭션이 커밋되기 전에
                // AFTER_COMMIT 리스너가 발화해 미커밋 데이터를 읽을 수 있으므로, 여기서는 활성
                // 트랜잭션에 직접 발행해 커밋 후에 리스너가 실행되게 한다.
                eventPublisher.publishEvent(new WinningNumbersCollectedEvent(response.round(), true));
            }
            // 성공 감사 로그도 이 트랜잭션이 실제로 커밋된 뒤에만 남겨야 한다(B1) — REQUIRES_NEW로
            // 즉시 커밋하면 이 메서드의 트랜잭션이 나중에 실패해도 "성공" 로그가 이미 영구히 남는다.
            eventPublisher.publishEvent(new WinningNumberManualUpsertEvent(response.round(), caller));
            log.info("운영 수동 회차 저장 완료: round={} changed={} caller={}", response.round(), result.changed(), caller);
            return response;
        } catch (RuntimeException exception) {
            winningNumberOperationLogService.logFailure(
                    WinningNumberOperationType.MANUAL_UPSERT,
                    request.round(),
                    caller,
                    exception.getMessage()
            );
            throw exception;
        }
    }

    // @Transactional을 두지 않는다 — collectLatest() 내부의 외부 HTTP fetch(fetchRound)가
    // 트랜잭션 밖에서 실행돼야 커넥션을 길게 점유하지 않는다(P1-3). 실제 저장은
    // WinningNumberCommandService가 호출당 짧은 트랜잭션으로 처리한다.
    public WinningNumberResponse collectLatestWinningNumber() {
        log.info("운영 최신 회차 수집 요청: caller={}", callerDetail());
        return winningNumberCollectionService.collectLatest();
    }

    public WinningNumberResponse collectWinningNumber(int round) {
        if (round < 1) {
            throw new ApiException(ApiErrorCode.INVALID_ROUND, "회차는 1 이상이어야 합니다.");
        }
        log.info("운영 특정 회차 수집 요청: round={} caller={}", round, callerDetail());
        return winningNumberCollectionService.collectRound(round);
    }

    private static String callerDetail() {
        String ip = MDC.get(RequestIdFilter.MDC_CLIENT_IP);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return "ops-api ip=" + (ip != null ? ip : "unknown") + " requestId=" + (requestId != null ? requestId : "none");
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, ApiErrorCode errorCode, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(errorCode, message);
        }
    }

    private WinningNumberOperationType parseOperationType(String value) {
        return parseEnum(WinningNumberOperationType.class, value,
                ApiErrorCode.INVALID_OPERATION_TYPE, "지원하지 않는 작업 유형입니다.");
    }

    private WinningNumberOperationStatus parseExecutionStatus(String value) {
        return parseEnum(WinningNumberOperationStatus.class, value,
                ApiErrorCode.INVALID_EXECUTION_STATUS, "지원하지 않는 실행 상태입니다.");
    }

    private OffsetDateTime parseDate(String value, long plusDays, ApiErrorCode errorCode, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim())
                    .plusDays(plusDays)
                    .atStartOfDay(KST)
                    .toOffsetDateTime();
        } catch (RuntimeException exception) {
            throw new ApiException(errorCode, message);
        }
    }

    private OffsetDateTime parseFromDate(String value) {
        return parseDate(value, 0, ApiErrorCode.INVALID_FROM_DATE, "from 날짜 형식이 올바르지 않습니다. yyyy-MM-dd 형식을 사용하세요.");
    }

    private OffsetDateTime parseToDateExclusive(String value) {
        return parseDate(value, 1, ApiErrorCode.INVALID_TO_DATE, "to 날짜 형식이 올바르지 않습니다. yyyy-MM-dd 형식을 사용하세요.");
    }
}
