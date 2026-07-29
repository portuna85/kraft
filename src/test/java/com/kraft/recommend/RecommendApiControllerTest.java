package com.kraft.recommend;

import com.kraft.winningnumber.WinningNumber;
import com.kraft.winningnumber.WinningNumberRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("번호 조합 조회 컨트롤러 테스트")
class RecommendApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WinningNumberRepository winningNumberRepository;

    @BeforeEach
    void setUp() {
        winningNumberRepository.deleteAll();
        winningNumberRepository.save(new WinningNumber(
                100, LocalDate.of(2026, 7, 25),
                1, 2, 3, 4, 5, 6, 7,
                2_500_000_000L, 0L, 0, 0L, 0L, OffsetDateTime.now()));
    }

    @Test
    @DisplayName("유효한 6개 번호는 200을 반환한다")
    void check_validSixNumbers_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/numbers/check")
                        .param("numbers", "1", "2", "3", "4", "5", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wonFirstPrize").value(true))
                .andExpect(jsonPath("$.firstPrizeHistory[0].round").value(100))
                .andExpect(jsonPath("$.firstPrizeHistory[0].drawDate").value("2026-07-25"))
                .andExpect(jsonPath("$.firstPrizeHistory[0].firstPrizeAmount").value(2_500_000_000L));
    }

    @Test
    @DisplayName("당첨되지 않은 조합은 빈 당첨 내역을 반환한다")
    void check_neverWinningNumbers_returnsEmptyHistory() throws Exception {
        mockMvc.perform(get("/api/v1/numbers/check")
                        .param("numbers", "7", "8", "9", "10", "11", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wonFirstPrize").value(false))
                .andExpect(jsonPath("$.firstPrizeHistory").isEmpty());
    }

    @Test
    @DisplayName("45 초과 번호가 섞이면 400을 반환한다")
    void check_numberOutOfRange_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/numbers/check")
                        .param("numbers", "1", "2", "3", "4", "5", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6개가 아닌 개수가 오면 400을 반환한다")
    void check_wrongCount_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/numbers/check")
                        .param("numbers", "1", "2", "3"))
                .andExpect(status().isBadRequest());
    }
}
