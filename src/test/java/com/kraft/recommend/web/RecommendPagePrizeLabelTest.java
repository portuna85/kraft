package com.kraft.recommend.web;

import com.kraft.recommend.domain.DrawDetails;
import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.domain.WinningDrawRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1등 당첨금의 세후 금액은 원천징수 세율로 계산한 추정치다(LottoPrizeTax). 확정액처럼 읽히는
 * "실수령액" 대신 "세후 예상 금액"으로 표시하는지 실제 렌더링으로 확인한다(평가 보고서 2026-09-25 §6).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecommendPagePrizeLabelTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WinningDrawRepository winningDrawRepository;

    @Test
    @DisplayName("최신 회차에 1등 당첨금이 있으면 세후 금액을 '세후 예상 금액'으로 보여준다")
    void prizeAfterTax_isLabeledAsEstimate() throws Exception {
        WinningDraw latest = WinningDraw.builder()
                .roundNo(999_999)
                .numbers(List.of(2, 4, 10, 16, 31, 41))
                .updatedAt(LocalDateTime.now())
                .build();
        latest.applyDetails(new DrawDetails(9, LocalDate.of(2026, 9, 19), 9, 3_281_029_250L));
        winningDrawRepository.save(latest);

        String html = mockMvc.perform(get("/recommend"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<dt>세후 예상 금액</dt>");
        assertThat(html).doesNotContain("실수령액");
    }
}
