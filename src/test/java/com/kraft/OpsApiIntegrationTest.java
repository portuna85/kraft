package com.kraft;

import com.kraft.winningnumber.WinningNumbersCollectedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
@DisplayName("운영 기능 통합 테스트")
class OpsApiIntegrationTest extends BaseApiIntegrationTest {

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    @DisplayName("운영 요약 정보 조회 시 토큰이 필요하며 최신 회차 정보를 반환하는지 확인")
    void opsSummaryRequiresTokenAndReturnsLatestRound() throws Exception {
        mockMvc.perform(get("/ops/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("OPS_UNAUTHORIZED")));

        mockMvc.perform(get("/ops/summary").header("X-Ops-Token", "test-ops-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("정상")))
                .andExpect(jsonPath("$.timezone", is("Asia/Seoul")))
                .andExpect(jsonPath("$.latestRound", is(2)));
    }

    // I-10: X-Content-Type-Options·X-Frame-Options는 caddy/Caddyfile이 사이트 전역으로
    // 발급하는 단일 소스로 옮겼다(SecurityHeadersFilter·Spring Security 기본값 모두 껐다) —
    // MockMvc는 Caddy를 거치지 않으므로 여기서는 더 이상 확인할 수 없다. 이 필터가 여전히
    // 책임지는 CSP만 검증한다.
    @Test
    @DisplayName("토큰 없이 접근해 401을 받아도 CSP 헤더가 붙는다 (필터 순서 회귀)")
    void opsUnauthorizedResponse_stillCarriesSecurityHeaders() throws Exception {
        mockMvc.perform(get("/ops/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    @DisplayName("운영 로그 조회 시 토큰이 필요하며 최근 항목을 반환하는지 확인")
    void opsLogsRequiresTokenAndReturnsRecentEntries() throws Exception {
        MvcResult result = mockMvc.perform(post("/ops/rounds")
                        .header("X-Ops-Token", "test-ops-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "round": 3,
                                  "drawDate": "2026-06-20",
                                  "numbers": [5, 12, 18, 27, 36, 44],
                                  "bonusNumber": 9,
                                  "firstPrizeAmount": 2100000000
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");

        mockMvc.perform(get("/ops/logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("OPS_UNAUTHORIZED")));

        mockMvc.perform(get("/ops/logs")
                        .header("X-Ops-Token", "test-ops-token")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].operationType", is("MANUAL_UPSERT")))
                .andExpect(jsonPath("$.items[0].executionStatus", is("SUCCESS")))
                .andExpect(jsonPath("$.items[0].round", is(3)))
                .andExpect(jsonPath("$.items[0].requestId", is(requestId)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(10)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("운영 로그 조회 시 유형, 상태, 회차별 필터링을 지원하는지 확인")
    void opsLogsSupportsFilteringByTypeStatusAndRound() throws Exception {
        mockMvc.perform(post("/ops/rounds")
                        .header("X-Ops-Token", "test-ops-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "round": 3,
                                  "drawDate": "2026-06-20",
                                  "numbers": [5, 12, 18, 27, 36, 44],
                                  "bonusNumber": 9,
                                  "firstPrizeAmount": 2100000000
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/ops/collect/latest")
                        .header("X-Ops-Token", "test-ops-token"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ops/logs")
                        .header("X-Ops-Token", "test-ops-token")
                        .param("operationType", "external_collect")
                        .param("executionStatus", "success")
                        .param("round", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].operationType", is("EXTERNAL_COLLECT")))
                .andExpect(jsonPath("$.items[0].executionStatus", is("SUCCESS")))
                .andExpect(jsonPath("$.items[0].round", is(4)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("운영 로그 조회 시 알 수 없는 필터 값에 대해 에러를 반환하는지 확인")
    void opsLogsRejectsUnknownFilterValues() throws Exception {
        mockMvc.perform(get("/ops/logs")
                        .header("X-Ops-Token", "test-ops-token")
                        .param("operationType", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_OPERATION_TYPE")));

        mockMvc.perform(get("/ops/logs")
                        .header("X-Ops-Token", "test-ops-token")
                        .param("executionStatus", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_EXECUTION_STATUS")));
    }

    @Test
    @DisplayName("운영 로그 조회 시 한국 표준시 기준 날짜 범위 필터링을 지원하는지 확인")
    void opsLogsSupportsDateRangeFilteringInKst() throws Exception {
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();

        mockMvc.perform(post("/ops/rounds")
                        .header("X-Ops-Token", "test-ops-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "round": 3,
                                  "drawDate": "2026-06-20",
                                  "numbers": [5, 12, 18, 27, 36, 44],
                                  "bonusNumber": 9,
                                  "firstPrizeAmount": 2100000000
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ops/logs")
                        .header("X-Ops-Token", "test-ops-token")
                        .param("from", today)
                        .param("to", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)));

        mockMvc.perform(get("/ops/logs")
                        .header("X-Ops-Token", "test-ops-token")
                        .param("from", "2099-01-01")
                        .param("to", "2099-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    @DisplayName("운영 로그 조회 시 잘못된 날짜 형식의 필터에 대해 에러를 반환하는지 확인")
    void opsLogsRejectsInvalidDateFilters() throws Exception {
        mockMvc.perform(get("/ops/logs")
                        .header("X-Ops-Token", "test-ops-token")
                        .param("from", "2026/06/13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_FROM_DATE")));

        mockMvc.perform(get("/ops/logs")
                        .header("X-Ops-Token", "test-ops-token")
                        .param("to", "2026/06/13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_TO_DATE")));
    }

    @Test
    @DisplayName("운영 회차 수동 등록 시 회차가 생성되거나 업데이트되는지 확인")
    void opsRoundUpsertCreatesAndUpdatesRound() throws Exception {
        mockMvc.perform(post("/ops/rounds")
                        .header("X-Ops-Token", "test-ops-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "round": 3,
                                  "drawDate": "2026-06-20",
                                  "numbers": [5, 12, 18, 27, 36, 44],
                                  "bonusNumber": 9,
                                  "firstPrizeAmount": 2100000000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round", is(3)))
                .andExpect(jsonPath("$.bonusNumber", is(9)));

        org.assertj.core.api.Assertions.assertThat(winningNumberOperationLogRepository.findAll())
                .hasSize(1);

        mockMvc.perform(post("/ops/rounds")
                        .header("X-Ops-Token", "test-ops-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "round": 3,
                                  "drawDate": "2026-06-20",
                                  "numbers": [6, 13, 19, 28, 37, 45],
                                  "bonusNumber": 10,
                                  "firstPrizeAmount": 2200000000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round", is(3)))
                .andExpect(jsonPath("$.numbers[0]", is(6)))
                .andExpect(jsonPath("$.bonusNumber", is(10)));

        var saved = winningNumberRepository.findByRound(3).orElseThrow();
        assertThat(saved.getBonusNumber()).isEqualTo(10);
    }

    @Test
    @DisplayName("수동 회차 보정이 변경을 일으키면 수집 이벤트가 발행된다")
    void opsRoundUpsert_whenChanged_publishesCollectedEvent() throws Exception {
        mockMvc.perform(post("/ops/rounds")
                        .header("X-Ops-Token", "test-ops-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "round": 3,
                                  "drawDate": "2026-06-20",
                                  "numbers": [5, 12, 18, 27, 36, 44],
                                  "bonusNumber": 9,
                                  "firstPrizeAmount": 2100000000
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(applicationEvents.stream(WinningNumbersCollectedEvent.class))
                .anyMatch(event -> event.round() == 3 && event.dataChanged());
    }

    @Test
    @DisplayName("변경 없는 수동 보정은 이벤트를 발행하지 않는다")
    void opsRoundUpsert_whenUnchanged_doesNotPublishEvent() throws Exception {
        String payload = """
                {
                  "round": 3,
                  "drawDate": "2026-06-20",
                  "numbers": [5, 12, 18, 27, 36, 44],
                  "bonusNumber": 9,
                  "firstPrizeAmount": 2100000000
                }
                """;

        mockMvc.perform(post("/ops/rounds")
                        .header("X-Ops-Token", "test-ops-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // 동일 내용으로 다시 보정 — changed=false여야 하므로 두 번째 요청은 이벤트를 발행하지 않는다
        long beforeCount = applicationEvents.stream(WinningNumbersCollectedEvent.class).count();

        mockMvc.perform(post("/ops/rounds")
                        .header("X-Ops-Token", "test-ops-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        long afterCount = applicationEvents.stream(WinningNumbersCollectedEvent.class).count();
        assertThat(afterCount).isEqualTo(beforeCount);
    }
}
