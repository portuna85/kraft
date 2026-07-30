package com.kraft.statistics;

import com.kraft.winningnumber.WinningNumberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("통계 컨트롤러 테스트")
class StatisticsApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WinningNumberRepository winningNumberRepository;

    @Autowired
    private FrequencySummaryRepository frequencySummaryRepository;

    @Autowired
    private PatternStatsSummaryRepository patternStatsSummaryRepository;

    @Autowired
    private CompanionPairSummaryRepository companionPairSummaryRepository;

    @Autowired
    private StatisticsSummaryRebuilder summaryRebuilder;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @BeforeEach
    void setUp() {
        frequencySummaryRepository.deleteAll();
        patternStatsSummaryRepository.deleteAll();
        companionPairSummaryRepository.deleteAll();
        winningNumberRepository.deleteAll();

        winningNumberRepository.save(com.kraft.winningnumber.WinningNumberTestFactory.create(1, LocalDate.of(2026, 1, 4),
                1, 2, 3, 4, 5, 6, 7,
                1_000_000_000L, 0L, 0, 0L, 0L,
                OffsetDateTime.now(Clock.system(KST))));
        winningNumberRepository.save(com.kraft.winningnumber.WinningNumberTestFactory.create(2, LocalDate.of(2026, 1, 11),
                10, 20, 30, 40, 41, 42, 43,
                2_000_000_000L, 0L, 0, 0L, 0L,
                OffsetDateTime.now(Clock.system(KST))));
    }

    @Test
    @DisplayName("번호별 출현 빈도 조회 시 비어있으면 재생성하고 200을 반환하는지 확인")
    void getFrequency_returns200_andTriggersRebuildWhenEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/stats/frequency"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60, must-revalidate"))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.totalRounds").value(2))
                .andExpect(jsonPath("$.frequencies", hasSize(45)));
    }

    @Test
    @DisplayName("패턴 통계 조회 시 200을 반환하는지 확인")
    void getPatterns_returns200() throws Exception {
        summaryRebuilder.rebuildAllSummaries();
        mockMvc.perform(get("/api/v1/stats/patterns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRounds").value(2));
    }

    @Test
    @DisplayName("동반 출연 번호 조회 시 200을 반환하는지 확인")
    void getCompanion_returns200() throws Exception {
        summaryRebuilder.rebuildAllSummaries();
        mockMvc.perform(get("/api/v1/stats/companion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRounds").value(2));
    }

    @Test
    @DisplayName("ball 파라미터로 해당 번호가 포함된 쌍만 반환한다")
    void getCompanion_withBallParam_returnsOnlyMatchingPairs() throws Exception {
        summaryRebuilder.rebuildAllSummaries();

        mockMvc.perform(get("/api/v1/stats/companion").param("ball", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRounds").value(2))
                .andExpect(jsonPath("$.topPairs[*].ballA").exists())
                .andExpect(result -> {
                    String json = result.getResponse().getContentAsString();
                    org.assertj.core.api.Assertions.assertThat(json)
                            .contains("\"ballA\":1")
                            .doesNotContain("\"ballA\":10,\"ballB\":20");
                });
    }

    @Test
    @DisplayName("ball이 1~45 밖이면 400을 반환한다")
    void getCompanion_withInvalidBall_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/stats/companion").param("ball", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BALL"));

        mockMvc.perform(get("/api/v1/stats/companion").param("ball", "46"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BALL"));
    }

    @Test
    @DisplayName("회차 조회가 공개 캐시 헤더를 반환하는지 확인")
    void roundsLatest_returnsCacheHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/rounds/latest"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60, must-revalidate"))
                .andExpect(header().exists("ETag"));
    }

    @Test
    @DisplayName("유효한 입력에 대해 번호 분석 결과가 올바른지 확인")
    void analysis_returnsCorrectMetrics_forValidInput() throws Exception {
        String body = "{\"numbers\":[1,2,3,4,5,6]}";

        mockMvc.perform(post("/api/v1/stats/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oddCount").value(3))
                .andExpect(jsonPath("$.evenCount").value(3))
                .andExpect(jsonPath("$.sumOfNumbers").value(21))
                .andExpect(jsonPath("$.sumBucket").value("21-65"))
                .andExpect(jsonPath("$.consecutivePairCount").value(5))
                .andExpect(jsonPath("$.lowCount").value(6))
                .andExpect(jsonPath("$.highCount").value(0))
                .andExpect(jsonPath("$.wonFirstPrize").value(true))
                .andExpect(jsonPath("$.firstPrizeHistory", hasSize(1)))
                .andExpect(jsonPath("$.firstPrizeHistory[0].round").value(1))
                .andExpect(jsonPath("$.firstPrizeHistory[0].drawDate").value("2026-01-04"))
                .andExpect(jsonPath("$.firstPrizeHistory[0].firstPrizeAmount").value(1_000_000_000L));
    }

    @Test
    @DisplayName("당첨 이력이 없는 조합은 명시적인 false와 빈 내역을 반환한다")
    void analysis_returnsEmptyFirstPrizeHistory_forNeverWinningCombination() throws Exception {
        mockMvc.perform(post("/api/v1/stats/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numbers\":[7,8,9,10,11,12]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wonFirstPrize").value(false))
                .andExpect(jsonPath("$.firstPrizeHistory", hasSize(0)));
    }

    @Test
    @DisplayName("범위를 벗어난 번호 입력 시 400 에러를 반환하는지 확인")
    void analysis_returns400_forNumberOutOfRange() throws Exception {
        String body = "{\"numbers\":[1,2,3,4,5,46]}";

        mockMvc.perform(post("/api/v1/stats/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("중복된 번호 입력 시 400 에러를 반환하는지 확인")
    void analysis_returns400_forDuplicateNumbers() throws Exception {
        String body = "{\"numbers\":[1,2,3,4,5,5]}";

        mockMvc.perform(post("/api/v1/stats/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("잘못된 번호 개수 입력 시 400 에러를 반환하는지 확인")
    void analysis_returns400_forWrongCount() throws Exception {
        String body = "{\"numbers\":[1,2,3,4,5]}";

        mockMvc.perform(post("/api/v1/stats/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
