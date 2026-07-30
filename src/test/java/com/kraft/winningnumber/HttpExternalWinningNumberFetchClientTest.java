package com.kraft.winningnumber;

import com.kraft.common.error.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("외부 로또 수집 클라이언트 — 회차 일치 검증·envelope 판별 테스트")
class HttpExternalWinningNumberFetchClientTest {

    // extractPayloadForRound는 HTTP 호출과 무관한 순수 판별 로직이라, 실제 RestClient 없이도
    // 생성자에 넘기는 협력자는 이 테스트에서 전혀 호출되지 않는다(null로 충분).
    private final HttpExternalWinningNumberFetchClient client =
            new HttpExternalWinningNumberFetchClient(null, new ExternalWinningNumberPayloadMapper());

    @Test
    @DisplayName("응답 회차가 요청 회차와 다르면 LOTTO_SOURCE_ROUND_MISMATCH를 던진다")
    void requireRoundMatch_mismatchedRound_throwsRoundMismatch() {
        WinningNumberUpsertRequest response = request(1202);

        assertThatThrownBy(() -> HttpExternalWinningNumberFetchClient.requireRoundMatch(1201, response))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(apiEx.getCode()).isEqualTo("LOTTO_SOURCE_ROUND_MISMATCH");
                });
    }

    @Test
    @DisplayName("응답 회차가 요청 회차와 같으면 예외 없이 통과한다")
    void requireRoundMatch_matchingRound_doesNotThrow() {
        WinningNumberUpsertRequest response = request(1201);

        assertThatCode(() -> HttpExternalWinningNumberFetchClient.requireRoundMatch(1201, response))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("KB-18: 미지 envelope 구조(data가 Map이 아님)는 예외 없이 body 자체를 구형 flat 응답으로 폴백한다")
    void unknownEnvelope_dataNotAMap_fallsBackToFlatBody() {
        Map<String, Object> body = Map.of(
                "data", "예상 밖 문자열 값",
                "drwNo", 1201
        );

        Map<?, ?> payload = client.extractPayloadForRound(body, 1201);

        assertThat(payload).isSameAs(body);
    }

    @Test
    @DisplayName("KB-18: data.list가 목록 항목마다 회차를 못 찾으면 LOTTO_SOURCE_ROUND_NOT_FOUND를 던진다")
    void unknownEnvelope_listWithoutMatchingRound_throwsRoundNotFound() {
        Map<String, Object> body = Map.of(
                "data", Map.of("list", List.of(
                        Map.of("ltEpsd", 9999),
                        "이것은 Map이 아닌 목록 항목"
                ))
        );

        assertThatThrownBy(() -> client.extractPayloadForRound(body, 1201))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(apiEx.getCode()).isEqualTo("LOTTO_SOURCE_ROUND_NOT_FOUND");
                });
    }

    private WinningNumberUpsertRequest request(int round) {
        return new WinningNumberUpsertRequest(
                round, LocalDate.of(2026, 6, 20), List.of(5, 12, 18, 27, 36, 44), 9,
                2_100_000_000L, null, null, null, null
        );
    }
}
