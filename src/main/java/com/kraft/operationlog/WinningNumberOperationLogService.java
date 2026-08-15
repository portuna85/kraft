package com.kraft.operationlog;

import com.kraft.common.web.PageResponse;
import com.kraft.common.web.RequestIdFilter;
import com.kraft.winningnumber.WinningNumberCollectionLoggedEvent;
import com.kraft.winningnumber.WinningNumberOperationType;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Transactional(readOnly = true)
public class WinningNumberOperationLogService {

    private static final int PUBLIC_INCIDENT_WINDOW_DAYS = 30;
    private static final int PUBLIC_INCIDENT_MAX = 20;

    private final WinningNumberOperationLogRepository repository;
    private final Clock clock;

    public WinningNumberOperationLogService(WinningNumberOperationLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(WinningNumberOperationType operationType,
                           Integer round,
                           String sourceDetail,
                           String message) {
        repository.save(new WinningNumberOperationLog(
                operationType,
                WinningNumberOperationStatus.SUCCESS,
                round,
                truncate(sourceDetail, 255),
                truncate(message, 500),
                truncate(MDC.get(RequestIdFilter.MDC_KEY), 100),
                OffsetDateTime.now(clock)
        ));
    }

    /**
     * OpsService.upsertWinningNumber()의 outer 트랜잭션이 실제로 커밋된 뒤에만 발화한다(B1).
     * 여기서는 이미 outer 트랜잭션이 끝난 시점이라 REQUIRES_NEW와 동일하게 새 트랜잭션에서
     * 커밋되지만, "커밋 전 성공 로그"라는 원래 문제(outer 롤백 시에도 로그가 남는 문제)는
     * 더 이상 발생하지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onManualUpsertCommitted(WinningNumberManualUpsertEvent event) {
        logSuccess(WinningNumberOperationType.MANUAL_UPSERT, event.round(), event.caller(),
                "운영 수동 회차 저장에 성공했습니다.");
    }

    /**
     * KB-17: winningnumber→operationlog 직접 호출 대신 이벤트로 역전한 경로.
     * {@link com.kraft.winningnumber.WinningNumberCollectionService}는 외부 HTTP fetch를
     * 트랜잭션 밖에서 수행하도록 class-level {@code @Transactional}을 두지 않으므로, 이
     * 리스너는 {@code @TransactionalEventListener}(AFTER_COMMIT)로 만들면 활성 트랜잭션이
     * 없어 조용히 발화하지 않을 수 있다 — 평범한 {@code @EventListener}로 즉시(동기) 처리해
     * 기존 직접 호출과 같은 타이밍을 유지한다. logSuccess/logFailure 자체가 이미
     * REQUIRES_NEW라 독립 트랜잭션에서 커밋된다.
     */
    @EventListener
    public void onCollectionLogged(WinningNumberCollectionLoggedEvent event) {
        if (event.success()) {
            logSuccess(event.operationType(), event.round(), event.sourceDetail(), event.message());
        } else {
            logFailure(event.operationType(), event.round(), event.sourceDetail(), event.message());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(WinningNumberOperationType operationType,
                           Integer round,
                           String sourceDetail,
                           String message) {
        repository.save(new WinningNumberOperationLog(
                operationType,
                WinningNumberOperationStatus.FAILURE,
                round,
                truncate(sourceDetail, 255),
                truncate(message, 500),
                truncate(MDC.get(RequestIdFilter.MDC_KEY), 100),
                OffsetDateTime.now(clock)
        ));
    }

    @Transactional(readOnly = true)
    public PageResponse<WinningNumberOperationLogResponse> getRecentLogs(int page, int size,
                                                                        WinningNumberOperationLogFilter filter) {
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
        var result = repository.findAll((root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            if (filter.operationType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("operationType"), filter.operationType()));
            }
            if (filter.executionStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("executionStatus"), filter.executionStatus()));
            }
            if (filter.round() != null) {
                predicates.add(criteriaBuilder.equal(root.get("round"), filter.round()));
            }
            if (filter.createdFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
            }
            if (filter.createdToExclusive() != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), filter.createdToExclusive()));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        }, pageable);
        return PageResponse.from(result.map(WinningNumberOperationLogResponse::from));
    }

    @Transactional(readOnly = true)
    public List<PublicIncidentResponse> getPublicIncidents() {
        OffsetDateTime since = OffsetDateTime.now(clock).minusDays(PUBLIC_INCIDENT_WINDOW_DAYS);
        List<WinningNumberOperationLog> logs = repository.findNotableSince(since);

        // (유형, 회차)로 그룹핑 — 같은 회차의 반복 실패/재시도가 카드로 중복 노출되지 않게 한다.
        // findNotableSince가 이미 createdAt DESC로 정렬돼 있어, 각 키의 "첫 등장"이 그 그룹의
        // 최신 로그이고, LinkedHashMap의 삽입 순서가 그대로 그룹별 최신순 정렬이 된다.
        Map<String, List<WinningNumberOperationLog>> grouped = logs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getOperationType() + ":" + log.getRound(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<List<WinningNumberOperationLog>> topGroups = grouped.values().stream()
                .limit(PUBLIC_INCIDENT_MAX)
                .toList();

        Set<Integer> roundsNeedingResolution = topGroups.stream()
                .map(group -> group.get(0))
                .filter(latest -> latest.getExecutionStatus() != WinningNumberOperationStatus.SUCCESS
                        && latest.getRound() != null)
                .map(WinningNumberOperationLog::getRound)
                .collect(Collectors.toSet());

        // 건별 existsSuccessForRoundAfter 대신 한 번의 round IN 쿼리로 해결 여부를 일괄 조회한다.
        Map<Integer, OffsetDateTime> latestSuccessByRound = roundsNeedingResolution.isEmpty()
                ? Map.of()
                : repository.findLatestSuccessTimestampsForRounds(roundsNeedingResolution).stream()
                        .collect(Collectors.toMap(RoundLatestSuccess::round, RoundLatestSuccess::latestSuccessAt));

        return topGroups.stream()
                .map(group -> {
                    WinningNumberOperationLog latest = group.get(0);
                    return new PublicIncidentResponse(
                            latest.getRound(),
                            describe(latest),
                            isResolved(latest, latestSuccessByRound),
                            latest.getCreatedAt(),
                            group.size()
                    );
                })
                .toList();
    }

    private String describe(WinningNumberOperationLog log) {
        boolean failed = log.getExecutionStatus() == WinningNumberOperationStatus.FAILURE;
        return switch (log.getOperationType()) {
            case EXTERNAL_COLLECT -> failed ? "자동 수집 지연" : "자동 수집";
            case MANUAL_UPSERT -> failed ? "수동 보정 실패" : "데이터 수동 보정";
        };
    }

    private boolean isResolved(WinningNumberOperationLog latest, Map<Integer, OffsetDateTime> latestSuccessByRound) {
        if (latest.getExecutionStatus() == WinningNumberOperationStatus.SUCCESS) {
            return true;
        }
        if (latest.getRound() == null) {
            return false;
        }
        OffsetDateTime latestSuccess = latestSuccessByRound.get(latest.getRound());
        return latestSuccess != null && latestSuccess.isAfter(latest.getCreatedAt());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
