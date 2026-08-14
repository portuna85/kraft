package com.kraft.winningnumber;

import com.kraft.common.error.ApiException;
import com.kraft.common.config.ExternalLottoProperties;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("외부 로또 수집 클라이언트 — 회차 일치 검증·envelope 판별 테스트")
class HttpExternalWinningNumberFetchClientTest {

    // extractPayloadForRound는 HTTP 호출과 무관한 순수 판별 로직이라, 실제 RestClient 없이도
    // 생성자에 넘기는 협력자는 이 테스트에서 전혀 호출되지 않는다(null로 충분).
    private final HttpExternalWinningNumberFetchClient client =
            new HttpExternalWinningNumberFetchClient(null, new ExternalWinningNumberPayloadMapper());

    @Test
    @DisplayName("HTTP 성공 응답을 매핑하고 외부 소스에 필요한 헤더를 보낸다")
    void fetchRound_success_mapsResponseAndRequiredHeaders() {
        TestClient testClient = testClient();
        testClient.server().expect(requestTo("https://lotto.example/1201"))
                .andExpect(header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(header("Referer", "https://lotto.example/results"))
                .andRespond(withSuccess("""
                        {"drwNo":1201,"drwNoDate":"2026-06-20","drwtNo1":5,"drwtNo2":12,
                         "drwtNo3":18,"drwtNo4":27,"drwtNo5":36,"drwtNo6":44,"bnusNo":9,
                         "firstWinamnt":2100000000}
                        """, MediaType.APPLICATION_JSON));

        WinningNumberUpsertRequest response = testClient.client().fetchRound(1201);

        assertThat(response.round()).isEqualTo(1201);
        assertThat(response.numbers()).containsExactly(5, 12, 18, 27, 36, 44);
        testClient.server().verify();
    }

    @Test
    @DisplayName("외부 5xx는 재시도 가능한 HttpServerErrorException으로 유지한다")
    void fetchRound_serverError_preservesRetryableException() {
        TestClient testClient = testClient();
        testClient.server().expect(requestTo("https://lotto.example/1201"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> testClient.client().fetchRound(1201))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    @DisplayName("외부 I/O 오류는 재시도 가능한 ResourceAccessException으로 유지한다")
    void fetchRound_ioFailure_preservesRetryableException() {
        TestClient testClient = testClient();
        testClient.server().expect(requestTo("https://lotto.example/1201"))
                .andRespond(withException(new IOException("upstream timeout")));

        assertThatThrownBy(() -> testClient.client().fetchRound(1201))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("손상된 JSON은 계약 오류로 실패하며 HTTP 재시도 예외로 위장하지 않는다")
    void fetchRound_malformedJson_isNotRetryableTransportFailure() {
        TestClient testClient = testClient();
        testClient.server().expect(requestTo("https://lotto.example/1201"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> testClient.client().fetchRound(1201))
                .isInstanceOf(RestClientException.class)
                .isNotInstanceOf(ResourceAccessException.class)
                .isNotInstanceOf(HttpServerErrorException.class);
    }

    @Test
    @DisplayName("빈 JSON 응답은 안정적인 LOTTO_SOURCE_EMPTY 오류로 변환한다")
    void fetchRound_emptyObject_throwsStableApiError() {
        TestClient testClient = testClient();
        testClient.server().expect(requestTo("https://lotto.example/1201"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> testClient.client().fetchRound(1201))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getCode())
                        .isEqualTo("LOTTO_SOURCE_EMPTY"));
    }

    @Test
    @DisplayName("회로가 열리면 안정적인 LOTTO_SOURCE_CIRCUIT_OPEN 오류로 변환한다")
    void fetchRoundFallback_openCircuit_throwsStableApiError() {
        TestClient testClient = testClient();

        assertThatThrownBy(() -> testClient.client().fetchRoundFallback(1201, null))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getCode())
                        .isEqualTo("LOTTO_SOURCE_CIRCUIT_OPEN"));
    }

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

    private TestClient testClient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalLottoProperties properties = new ExternalLottoProperties(
                "https://lotto.example/{round}", "0 0 0 * * *",
                "https://lotto.example/results", "XMLHttpRequest");
        return new TestClient(new HttpExternalWinningNumberFetchClient(
                properties, new ExternalWinningNumberPayloadMapper(), builder.build()), server);
    }

    private record TestClient(HttpExternalWinningNumberFetchClient client, MockRestServiceServer server) {
    }
}
