package com.kraft.recommend.web;

import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.domain.WinningDrawRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 최신 회차 당첨번호를 모델에 담아 서버가 직접 렌더링하는지 확인한다({@code recommend.html}이
 * {@code latestRoundNo}·{@code latestRoundNumbers}를 읽는다).
 */
@ExtendWith(MockitoExtension.class)
class RecommendationPageControllerTest {

    @Mock
    private WinningDrawRepository winningDrawRepository;

    @Test
    @DisplayName("최신 회차가 있으면 회차 번호와 당첨번호를 모델에 담는다")
    void addsLatestRoundToModel_whenHistoryExists() {
        WinningDraw latest = WinningDraw.builder()
                .roundNo(1242)
                .numbers(List.of(2, 4, 10, 16, 31, 41))
                .updatedAt(LocalDateTime.now())
                .build();
        given(winningDrawRepository.findTopByOrderByRoundNoDesc()).willReturn(Optional.of(latest));
        Model model = new ExtendedModelMap();

        String view = new RecommendationPageController(winningDrawRepository).recommend(model);

        assertThat(view).isEqualTo("recommend/recommend");
        assertThat(model.getAttribute("latestRoundNo")).isEqualTo(1242);
        assertThat(model.getAttribute("latestRoundNumbers")).isEqualTo(List.of(2, 4, 10, 16, 31, 41));
    }

    @Test
    @DisplayName("이력이 비어 있으면 최신 회차 속성을 담지 않는다")
    void omitsLatestRound_whenHistoryEmpty() {
        given(winningDrawRepository.findTopByOrderByRoundNoDesc()).willReturn(Optional.empty());
        Model model = new ExtendedModelMap();

        new RecommendationPageController(winningDrawRepository).recommend(model);

        assertThat(model.containsAttribute("latestRoundNo")).isFalse();
        assertThat(model.containsAttribute("latestRoundNumbers")).isFalse();
    }
}
