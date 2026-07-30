package com.kraft.winningnumber;

import com.kraft.common.error.ApiException;
import org.junit.jupiter.api.DisplayName;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("외부 당첨 번호 페이로드 매퍼 테스트")
class ExternalWinningNumberPayloadMapperTest {

    private final ExternalWinningNumberPayloadMapper mapper = new ExternalWinningNumberPayloadMapper();

    @Test
    @DisplayName("공식 스타일 페이로드가 올바르게 매핑되는지 확인")
    void mapsOfficialStylePayload() {
        WinningNumberUpsertRequest request = mapper.toRequest(Map.ofEntries(
                Map.entry("returnValue", "success"),
                Map.entry("drwNo", 1201),
                Map.entry("drwNoDate", "2026-06-20"),
                Map.entry("drwtNo1", 5),
                Map.entry("drwtNo2", 12),
                Map.entry("drwtNo3", 18),
                Map.entry("drwtNo4", 27),
                Map.entry("drwtNo5", 36),
                Map.entry("drwtNo6", 44),
                Map.entry("bnusNo", 9),
                Map.entry("firstWinamnt", 2100000000L)
        ));

        assertThat(request.round()).isEqualTo(1201);
        assertThat(request.drawDate().toString()).isEqualTo("2026-06-20");
        assertThat(request.numbers()).containsExactly(5, 12, 18, 27, 36, 44);
        assertThat(request.bonusNumber()).isEqualTo(9);
        assertThat(request.firstPrizeAmount()).isEqualTo(2100000000L);
    }

    @Test
    @DisplayName("새 외부 응답 형식의 날짜와 번호 필드가 올바르게 매핑되는지 확인")
    void mapsNewLt645StylePayload() {
        WinningNumberUpsertRequest request = mapper.toRequest(Map.ofEntries(
                Map.entry("ltEpsd", 1227),
                Map.entry("ltRflYmd", "20260606"),
                Map.entry("tm1WnNo", 3),
                Map.entry("tm2WnNo", 11),
                Map.entry("tm3WnNo", 19),
                Map.entry("tm4WnNo", 28),
                Map.entry("tm5WnNo", 35),
                Map.entry("tm6WnNo", 42),
                Map.entry("bnsWnNo", 7),
                Map.entry("rnk1WnAmt", 1500000000L),
                Map.entry("rnk1SumWnAmt", 3000000000L),
                Map.entry("rnk2WnAmt", 50000000L),
                Map.entry("rnk2WnNope", 60),
                Map.entry("rlvtEpsdSumNtslAmt", 55000000000L)
        ));

        assertThat(request.round()).isEqualTo(1227);
        assertThat(request.drawDate().toString()).isEqualTo("2026-06-06");
        assertThat(request.numbers()).containsExactly(3, 11, 19, 28, 35, 42);
        assertThat(request.bonusNumber()).isEqualTo(7);
        assertThat(request.firstPrizeAmount()).isEqualTo(1500000000L);
        assertThat(request.firstAccumAmount()).isEqualTo(3000000000L);
        assertThat(request.secondPrize()).isEqualTo(50000000L);
        assertThat(request.secondWinners()).isEqualTo(60);
        assertThat(request.totalSales()).isEqualTo(55000000000L);
    }

    @Test
    @DisplayName("KB-18: 필수 필드(보너스 번호)가 누락되면 LOTTO_SOURCE_PARSE_ERROR를 던진다")
    void missingRequiredField_throwsParseError() {
        // bnusNo/bnsWnNo 둘 다 없음 — round/drawDate/numbers는 있어도 필수 필드 하나만
        // 빠져도 조용히 null로 저장되지 않고 여기서 막혀야 한다.
        Map<String, Object> payload = Map.ofEntries(
                Map.entry("drwNo", 1201),
                Map.entry("drwNoDate", "2026-06-20"),
                Map.entry("drwtNo1", 5),
                Map.entry("drwtNo2", 12),
                Map.entry("drwtNo3", 18),
                Map.entry("drwtNo4", 27),
                Map.entry("drwtNo5", 36),
                Map.entry("drwtNo6", 44),
                Map.entry("firstWinamnt", 2100000000L)
        );

        assertThatThrownBy(() -> mapper.toRequest(payload))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(apiEx.getCode()).isEqualTo("LOTTO_SOURCE_PARSE_ERROR");
                });
    }

    @Test
    @DisplayName("KB-18: 숫자 필드에 숫자로 변환할 수 없는 값이 오면 LOTTO_SOURCE_PARSE_ERROR를 던진다")
    void fieldWithWrongType_throwsParseError() {
        Map<String, Object> payload = Map.ofEntries(
                Map.entry("drwNo", 1201),
                Map.entry("drwNoDate", "2026-06-20"),
                Map.entry("drwtNo1", "다섯"), // 숫자가 아닌 문자열
                Map.entry("drwtNo2", 12),
                Map.entry("drwtNo3", 18),
                Map.entry("drwtNo4", 27),
                Map.entry("drwtNo5", 36),
                Map.entry("drwtNo6", 44),
                Map.entry("bnusNo", 9),
                Map.entry("firstWinamnt", 2100000000L)
        );

        assertThatThrownBy(() -> mapper.toRequest(payload))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(apiEx.getCode()).isEqualTo("LOTTO_SOURCE_PARSE_ERROR");
                });
    }

    @Test
    @DisplayName("KB-18: numbers가 빈 리스트로 오면 6개가 아니므로 LOTTO_SOURCE_PARSE_ERROR를 던진다")
    void emptyNumbersList_throwsParseError() {
        Map<String, Object> payload = Map.ofEntries(
                Map.entry("drwNo", 1201),
                Map.entry("drwNoDate", "2026-06-20"),
                Map.entry("numbers", java.util.List.of()),
                Map.entry("bnusNo", 9),
                Map.entry("firstWinamnt", 2100000000L)
        );

        assertThatThrownBy(() -> mapper.toRequest(payload))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(apiEx.getCode()).isEqualTo("LOTTO_SOURCE_PARSE_ERROR");
                });
    }
}
