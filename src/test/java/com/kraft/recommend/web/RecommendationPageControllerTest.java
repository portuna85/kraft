package com.kraft.recommend.web;

import com.kraft.recommend.domain.DrawDetails;
import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.dto.LatestDrawView;
import com.kraft.recommend.service.RecommendationFreshness;
import com.kraft.recommend.service.LatestDrawService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 최신 회차 당첨번호를 모델에 담아 서버가 직접 렌더링하는지 확인한다. {@code lotto/latest-draw.html} 조각이 읽는 {@code latestDraw}({@link LatestDrawView}) 하나를 담는다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationPageControllerTest {

    @Mock
    private LatestDrawService latestDrawService;

    @Mock
    private RecommendationHistoryStateRepository stateRepository;

    private RecommendationFreshness freshness() {
        return new RecommendationFreshness(stateRepository, 200);
    }

    @Test
    @DisplayName("최신 회차가 있으면 회차 번호와 당첨번호를 모델에 담는다")
    void addsLatestRoundToModel_whenHistoryExists() {
        WinningDraw latest = WinningDraw.builder()
                .roundNo(1242)
                .numbers(List.of(2, 4, 10, 16, 31, 41))
                .updatedAt(LocalDateTime.now())
                .build();
        given(latestDrawService.latest()).willReturn(Optional.of(latest));
        Model model = new ExtendedModelMap();

        String view = new RecommendationPageController(latestDrawService, freshness()).recommend(model);

        assertThat(view).isEqualTo("recommend/recommend");
        LatestDrawView draw = (LatestDrawView) model.getAttribute("latestDraw");
        assertThat(draw.roundNo()).isEqualTo(1242);
        assertThat(draw.numbers()).isEqualTo(List.of(2, 4, 10, 16, 31, 41));
    }

    @Test
    @DisplayName("부가 정보(추첨일·보너스·1등 당첨금)가 있으면 세후 예상 금액을 계산해 함께 담는다")
    void addsPrizeDetails_whenPresent() {
        WinningDraw latest = WinningDraw.builder()
                .roundNo(1242)
                .numbers(List.of(2, 4, 10, 16, 31, 41))
                .updatedAt(LocalDateTime.now())
                .build();
        latest.applyDetails(new DrawDetails(9, LocalDate.of(2026, 9, 19), 9, 3_281_029_250L));
        given(latestDrawService.latest()).willReturn(Optional.of(latest));
        Model model = new ExtendedModelMap();

        new RecommendationPageController(latestDrawService, freshness()).recommend(model);

        LatestDrawView draw = (LatestDrawView) model.getAttribute("latestDraw");
        assertThat(draw.drawDate()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(draw.bonusNo()).isEqualTo(9);
        assertThat(draw.firstPrizeAmount()).isEqualTo(3_281_029_250L);
        assertThat(draw.winnerCount()).isEqualTo(9);
        assertThat(draw.takeHomeAmount()).isNotNull();
    }

    @Test
    @DisplayName("본번호는 있지만 부가 정보가 없으면(과거 데이터) 그 값만 null이라 조각이 해당 블록을 생략한다")
    void omitsPrizeDetails_whenLegacyRoundHasNone() {
        WinningDraw latest = WinningDraw.builder()
                .roundNo(1)
                .numbers(List.of(1, 2, 3, 4, 5, 6))
                .updatedAt(LocalDateTime.now())
                .build();
        given(latestDrawService.latest()).willReturn(Optional.of(latest));
        Model model = new ExtendedModelMap();

        new RecommendationPageController(latestDrawService, freshness()).recommend(model);

        LatestDrawView draw = (LatestDrawView) model.getAttribute("latestDraw");
        assertThat(draw.drawDate()).isNull();
        assertThat(draw.bonusNo()).isNull();
        assertThat(draw.firstPrizeAmount()).isNull();
        assertThat(draw.winnerCount()).isNull();
        assertThat(draw.takeHomeAmount()).isNull();
    }

    @Test
    @DisplayName("이력이 비어 있으면 최신 회차 속성을 담지 않는다")
    void omitsLatestRound_whenHistoryEmpty() {
        given(latestDrawService.latest()).willReturn(Optional.empty());
        Model model = new ExtendedModelMap();

        new RecommendationPageController(latestDrawService, freshness()).recommend(model);

        assertThat(model.containsAttribute("latestDraw")).isFalse();
    }

    @Test
    @DisplayName("검증 기준이 최근이면 회차·시각을 담고 지연 표시는 하지 않는다")
    void addsFreshHistoryStatus() {
        given(stateRepository.findById(1)).willReturn(Optional.of(RecommendationHistoryState.builder()
                .id(1).verifiedThroughRound(1242).verifiedAt(LocalDateTime.now().minusHours(3)).build()));
        Model model = new ExtendedModelMap();

        new RecommendationPageController(latestDrawService, freshness()).recommend(model);

        assertThat(model.getAttribute("historyReady")).isEqualTo(true);
        assertThat(model.getAttribute("historyStale")).isEqualTo(false);
        assertThat(model.getAttribute("historyVerifiedRound")).isEqualTo(1242);
    }

    @Test
    @DisplayName("임계 시간을 넘겨 갱신되지 않았으면 지연으로 표시한다")
    void marksStaleHistory() {
        given(stateRepository.findById(1)).willReturn(Optional.of(RecommendationHistoryState.builder()
                .id(1).verifiedThroughRound(1242).verifiedAt(LocalDateTime.now().minusHours(500)).build()));
        Model model = new ExtendedModelMap();

        new RecommendationPageController(latestDrawService, freshness()).recommend(model);

        assertThat(model.getAttribute("historyReady")).isEqualTo(true);
        assertThat(model.getAttribute("historyStale")).isEqualTo(true);
    }

    @Test
    @DisplayName("이력 상태가 없으면 준비되지 않은 것으로 표시한다")
    void marksNotReadyHistory() {
        Model model = new ExtendedModelMap();

        new RecommendationPageController(latestDrawService, freshness()).recommend(model);

        assertThat(model.getAttribute("historyReady")).isEqualTo(false);
    }
}
