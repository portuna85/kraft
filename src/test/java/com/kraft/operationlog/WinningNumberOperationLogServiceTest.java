package com.kraft.operationlog;

import com.kraft.winningnumber.WinningNumberCollectionLoggedEvent;
import com.kraft.winningnumber.WinningNumberOperationType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("당첨 번호 운영 로그 서비스 — 공개 인시던트 집계 테스트")
class WinningNumberOperationLogServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), KST);

    private final WinningNumberOperationLogRepository repository = mock(WinningNumberOperationLogRepository.class);
    private final WinningNumberOperationLogService service =
            new WinningNumberOperationLogService(repository, FIXED_CLOCK);

    @Test
    @DisplayName("같은 회차 실패 3회와 성공 1회는 해결된 카드 1장으로 집계된다")
    void getPublicIncidents_sameRoundThreeFailuresThenSuccess_aggregatesIntoOneResolvedCard() {
        OffsetDateTime t0 = OffsetDateTime.now(FIXED_CLOCK).minusHours(3);
        var logs = List.of(
                // findNotableSince는 createdAt DESC 순으로 반환 — 실패 로그만(성공은 EXTERNAL_COLLECT/SUCCESS라
                // findNotableSince 조건(FAILURE 또는 MANUAL_UPSERT)에 해당 안 되면 애초에 없음).
                // 여기서는 실패 3건만 "주목할 만한" 이력으로 온다고 가정한다.
                log(WinningNumberOperationType.EXTERNAL_COLLECT, WinningNumberOperationStatus.FAILURE, 1230, t0.plusHours(2)),
                log(WinningNumberOperationType.EXTERNAL_COLLECT, WinningNumberOperationStatus.FAILURE, 1230, t0.plusHours(1)),
                log(WinningNumberOperationType.EXTERNAL_COLLECT, WinningNumberOperationStatus.FAILURE, 1230, t0)
        );
        when(repository.findNotableSince(any())).thenReturn(logs);
        // 이후 성공이 마지막 실패(t0+2h)보다 늦게 기록됨
        when(repository.findLatestSuccessTimestampsForRounds(anyCollection()))
                .thenReturn(List.of(new RoundLatestSuccess(1230, t0.plusHours(3))));

        List<PublicIncidentResponse> result = service.getPublicIncidents();

        assertThat(result).hasSize(1);
        PublicIncidentResponse card = result.get(0);
        assertThat(card.round()).isEqualTo(1230);
        assertThat(card.occurrences()).isEqualTo(3);
        assertThat(card.resolved()).isTrue();
        assertThat(card.occurredAt()).isEqualTo(t0.plusHours(2));
    }

    @Test
    @DisplayName("성공이 마지막 실패보다 이전이면 미해결로 판정한다")
    void getPublicIncidents_successBeforeLastFailure_remainsUnresolved() {
        OffsetDateTime lastFailure = OffsetDateTime.now(FIXED_CLOCK);
        var logs = List.of(
                log(WinningNumberOperationType.EXTERNAL_COLLECT, WinningNumberOperationStatus.FAILURE, 1231, lastFailure)
        );
        when(repository.findNotableSince(any())).thenReturn(logs);
        // 성공이 실패보다 더 이전(과거)에 있었던 경우 — 미해결로 남아야 함
        when(repository.findLatestSuccessTimestampsForRounds(anyCollection()))
                .thenReturn(List.of(new RoundLatestSuccess(1231, lastFailure.minusDays(1))));

        List<PublicIncidentResponse> result = service.getPublicIncidents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).resolved()).isFalse();
    }

    @Test
    @DisplayName("집계 카드는 최근 발생순으로 최대 20개만 반환한다")
    void getPublicIncidents_limitsToTwentyGroupsInRecencyOrder() {
        OffsetDateTime base = OffsetDateTime.now(FIXED_CLOCK);
        List<WinningNumberOperationLog> logs = new java.util.ArrayList<>();
        // 25개의 서로 다른 회차, 최신순(내림차순)으로 정렬된 상태를 흉내낸다.
        for (int i = 0; i < 25; i++) {
            logs.add(log(WinningNumberOperationType.EXTERNAL_COLLECT, WinningNumberOperationStatus.FAILURE,
                    1300 - i, base.minusHours(i)));
        }
        when(repository.findNotableSince(any())).thenReturn(logs);
        when(repository.findLatestSuccessTimestampsForRounds(anyCollection())).thenReturn(List.of());

        List<PublicIncidentResponse> result = service.getPublicIncidents();

        assertThat(result).hasSize(20);
        assertThat(result.get(0).round()).isEqualTo(1300);
        assertThat(result.get(19).round()).isEqualTo(1281);
    }

    @Test
    @DisplayName("수동 보정 커밋 이벤트를 받으면 MANUAL_UPSERT 성공 로그를 남긴다(B1)")
    void onManualUpsertCommitted_recordsManualUpsertSuccessLog() {
        service.onManualUpsertCommitted(new WinningNumberManualUpsertEvent(1201, "ops-api ip=1.2.3.4 requestId=abc"));

        org.mockito.ArgumentCaptor<WinningNumberOperationLog> captor =
                org.mockito.ArgumentCaptor.forClass(WinningNumberOperationLog.class);
        verify(repository, times(1)).save(captor.capture());
        WinningNumberOperationLog saved = captor.getValue();
        assertThat(saved.getOperationType()).isEqualTo(WinningNumberOperationType.MANUAL_UPSERT);
        assertThat(saved.getExecutionStatus()).isEqualTo(WinningNumberOperationStatus.SUCCESS);
        assertThat(saved.getRound()).isEqualTo(1201);
        assertThat(saved.getSourceDetail()).isEqualTo("ops-api ip=1.2.3.4 requestId=abc");
    }

    @Test
    @DisplayName("KB-17: 수집 로그 이벤트를 받으면 성공 여부에 따라 logSuccess/logFailure로 위임한다")
    void onCollectionLogged_delegatesToLogSuccessOrLogFailureBySuccessFlag() {
        service.onCollectionLogged(new WinningNumberCollectionLoggedEvent(
                WinningNumberOperationType.EXTERNAL_COLLECT, true, 1201, "external-source", "성공"));
        service.onCollectionLogged(new WinningNumberCollectionLoggedEvent(
                WinningNumberOperationType.EXTERNAL_COLLECT, false, 1202, "external-source", "실패"));

        org.mockito.ArgumentCaptor<WinningNumberOperationLog> captor =
                org.mockito.ArgumentCaptor.forClass(WinningNumberOperationLog.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(WinningNumberOperationLog::getRound, WinningNumberOperationLog::getExecutionStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1201, WinningNumberOperationStatus.SUCCESS),
                        org.assertj.core.groups.Tuple.tuple(1202, WinningNumberOperationStatus.FAILURE));
    }

    private static WinningNumberOperationLog log(WinningNumberOperationType type,
                                                  WinningNumberOperationStatus status,
                                                  Integer round,
                                                  OffsetDateTime createdAt) {
        return new WinningNumberOperationLog(type, status, round, null, null, null, createdAt);
    }
}
