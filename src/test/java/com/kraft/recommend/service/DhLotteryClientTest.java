package com.kraft.recommend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link DhLotteryClient}가 성공/아직 추첨 전/신뢰할 수 없는 응답(봇 차단 HTML 등)을
 * 올바르게 구분하는지 확인한다. 실제 네트워크를 쓰지 않고 {@link MockRestServiceServer}로
 * 응답을 흉내낸다.
 */
class DhLotteryClientTest {

    private static final String BASE_URL = "https://www.dhlottery.co.kr";

    private DhLotteryClient newClient(MockRestServiceServer[] serverOut) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        serverOut[0] = MockRestServiceServer.bindTo(builder).build();
        return new DhLotteryClient(builder.build());
    }

    @Test
    @DisplayName("성공 응답이면 검증된 번호 6개를 담은 Success를 반환한다")
    void success_returnsValidatedDraw() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        DhLotteryClient client = newClient(serverOut);
        serverOut[0].expect(requestTo(BASE_URL + "/common.do?method=getLottoNumber&drwNo=1241"))
                .andRespond(withSuccess("""
                        {"returnValue":"success","drwNo":1241,
                         "drwtNo1":7,"drwtNo2":13,"drwtNo3":16,"drwtNo4":23,"drwtNo5":24,"drwtNo6":43,
                         "bnusNo":9}
                        """, MediaType.APPLICATION_JSON));

        DhLotteryClient.FetchOutcome outcome = client.fetchRound(1241);

        assertThat(outcome).isInstanceOf(DhLotteryClient.FetchOutcome.Success.class);
        ImportedDraw draw = ((DhLotteryClient.FetchOutcome.Success) outcome).draw();
        assertThat(draw.roundNo()).isEqualTo(1241);
        assertThat(draw.numbers()).containsExactlyInAnyOrder(7, 13, 16, 23, 24, 43);
    }

    @Test
    @DisplayName("returnValue가 success가 아니면 NotYetDrawn을 반환한다")
    void notYetDrawn_whenReturnValueIsNotSuccess() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        DhLotteryClient client = newClient(serverOut);
        serverOut[0].expect(requestTo(BASE_URL + "/common.do?method=getLottoNumber&drwNo=9999"))
                .andRespond(withSuccess("{\"returnValue\":\"fail\"}", MediaType.APPLICATION_JSON));

        DhLotteryClient.FetchOutcome outcome = client.fetchRound(9999);

        assertThat(outcome).isInstanceOf(DhLotteryClient.FetchOutcome.NotYetDrawn.class);
    }

    @Test
    @DisplayName("JSON이 아닌 응답(봇 차단 대기실 HTML 등)은 NotYetDrawn이 아니라 Unavailable로 분류한다")
    void unavailable_whenResponseIsNotJson() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        DhLotteryClient client = newClient(serverOut);
        serverOut[0].expect(requestTo(BASE_URL + "/common.do?method=getLottoNumber&drwNo=1241"))
                .andRespond(withSuccess("<html><body>서비스 접근 대기 중입니다</body></html>", MediaType.TEXT_HTML));

        DhLotteryClient.FetchOutcome outcome = client.fetchRound(1241);

        assertThat(outcome).isInstanceOf(DhLotteryClient.FetchOutcome.Unavailable.class);
    }

    @Test
    @DisplayName("HTTP 오류 응답도 Unavailable로 분류한다")
    void unavailable_onHttpError() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        DhLotteryClient client = newClient(serverOut);
        serverOut[0].expect(requestTo(BASE_URL + "/common.do?method=getLottoNumber&drwNo=1241"))
                .andRespond(withServerError());

        DhLotteryClient.FetchOutcome outcome = client.fetchRound(1241);

        assertThat(outcome).isInstanceOf(DhLotteryClient.FetchOutcome.Unavailable.class);
    }

    @Test
    @DisplayName("번호가 유효하지 않으면(범위·중복 오류) Unavailable로 분류한다")
    void unavailable_whenNumbersAreInvalid() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        DhLotteryClient client = newClient(serverOut);
        serverOut[0].expect(requestTo(BASE_URL + "/common.do?method=getLottoNumber&drwNo=1241"))
                .andRespond(withSuccess("""
                        {"returnValue":"success","drwNo":1241,
                         "drwtNo1":0,"drwtNo2":13,"drwtNo3":16,"drwtNo4":23,"drwtNo5":24,"drwtNo6":43}
                        """, MediaType.APPLICATION_JSON));

        DhLotteryClient.FetchOutcome outcome = client.fetchRound(1241);

        assertThat(outcome).isInstanceOf(DhLotteryClient.FetchOutcome.Unavailable.class);
    }
}
