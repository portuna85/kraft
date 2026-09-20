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
 * {@link DhLotteryClient}가 성공/아직 추첨 전/신뢰할 수 없는 응답(홈페이지로 리다이렉트되는
 * 등)을 올바르게 구분하는지, 그리고 한 번 받은 배치를 캐시해 같은 배치에 속한 회차는 다시
 * 요청하지 않는지 확인한다. 실제 네트워크를 쓰지 않고 {@link MockRestServiceServer}로
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
    @DisplayName("성공 응답이면 요청한 회차의 검증된 번호 6개를 담은 Success를 반환한다")
    void success_returnsValidatedDraw() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        DhLotteryClient client = newClient(serverOut);
        serverOut[0].expect(requestTo(BASE_URL + "/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd=1241"))
                .andRespond(withSuccess("""
                        {"resultCode":null,"resultMessage":null,"data":{"list":[
                          {"ltEpsd":1241,"tm1WnNo":7,"tm2WnNo":13,"tm3WnNo":16,"tm4WnNo":23,"tm5WnNo":24,"tm6WnNo":43,"bnsWnNo":9}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        DhLotteryClient.FetchOutcome outcome = client.fetchRound(1241);

        assertThat(outcome).isInstanceOf(DhLotteryClient.FetchOutcome.Success.class);
        ImportedDraw draw = ((DhLotteryClient.FetchOutcome.Success) outcome).draw();
        assertThat(draw.roundNo()).isEqualTo(1241);
        assertThat(draw.numbers()).containsExactlyInAnyOrder(7, 13, 16, 23, 24, 43);
    }

    @Test
    @DisplayName("배치에 여러 회차가 함께 오면 캐시해 두고, 같은 배치에 속한 다른 회차는 다시 요청하지 않는다")
    void success_cachesRestOfBatch() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        DhLotteryClient client = newClient(serverOut);
        serverOut[0].expect(requestTo(BASE_URL + "/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd=1239"))
                .andRespond(withSuccess("""
                        {"resultCode":null,"resultMessage":null,"data":{"list":[
                          {"ltEpsd":1240,"tm1WnNo":11,"tm2WnNo":13,"tm3WnNo":19,"tm4WnNo":20,"tm5WnNo":31,"tm6WnNo":44,"bnsWnNo":27},
                          {"ltEpsd":1239,"tm1WnNo":11,"tm2WnNo":13,"tm3WnNo":22,"tm4WnNo":32,"tm5WnNo":33,"tm6WnNo":36,"bnsWnNo":8}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        DhLotteryClient.FetchOutcome first = client.fetchRound(1239);
        DhLotteryClient.FetchOutcome second = client.fetchRound(1240);

        serverOut[0].verify();
        assertThat(((DhLotteryClient.FetchOutcome.Success) first).draw().roundNo()).isEqualTo(1239);
        assertThat(((DhLotteryClient.FetchOutcome.Success) second).draw().roundNo()).isEqualTo(1240);
    }

    @Test
    @DisplayName("응답 목록에 요청한 회차가 없으면 NotYetDrawn을 반환한다")
    void notYetDrawn_whenRequestedRoundMissingFromList() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        DhLotteryClient client = newClient(serverOut);
        serverOut[0].expect(requestTo(BASE_URL + "/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd=9999"))
                .andRespond(withSuccess("""
                        {"resultCode":null,"resultMessage":null,"data":{"list":[
                          {"ltEpsd":1242,"tm1WnNo":2,"tm2WnNo":4,"tm3WnNo":10,"tm4WnNo":16,"tm5WnNo":31,"tm6WnNo":41,"bnsWnNo":9}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        DhLotteryClient.FetchOutcome outcome = client.fetchRound(9999);

        assertThat(outcome).isInstanceOf(DhLotteryClient.FetchOutcome.NotYetDrawn.class);
    }

    @Test
    @DisplayName("JSON이 아닌 응답(홈페이지로 리다이렉트되는 HTML 등)은 NotYetDrawn이 아니라 Unavailable로 분류한다")
    void unavailable_whenResponseIsNotJson() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        DhLotteryClient client = newClient(serverOut);
        serverOut[0].expect(requestTo(BASE_URL + "/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd=1241"))
                .andRespond(withSuccess("<html><body>The document has been moved.</body></html>", MediaType.TEXT_HTML));

        DhLotteryClient.FetchOutcome outcome = client.fetchRound(1241);

        assertThat(outcome).isInstanceOf(DhLotteryClient.FetchOutcome.Unavailable.class);
    }

    @Test
    @DisplayName("HTTP 오류 응답도 Unavailable로 분류한다")
    void unavailable_onHttpError() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        DhLotteryClient client = newClient(serverOut);
        serverOut[0].expect(requestTo(BASE_URL + "/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd=1241"))
                .andRespond(withServerError());

        DhLotteryClient.FetchOutcome outcome = client.fetchRound(1241);

        assertThat(outcome).isInstanceOf(DhLotteryClient.FetchOutcome.Unavailable.class);
    }

    @Test
    @DisplayName("번호가 유효하지 않으면(범위·중복 오류) Unavailable로 분류한다")
    void unavailable_whenNumbersAreInvalid() {
        MockRestServiceServer[] serverOut = new MockRestServiceServer[1];
        DhLotteryClient client = newClient(serverOut);
        serverOut[0].expect(requestTo(BASE_URL + "/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd=1241"))
                .andRespond(withSuccess("""
                        {"resultCode":null,"resultMessage":null,"data":{"list":[
                          {"ltEpsd":1241,"tm1WnNo":0,"tm2WnNo":13,"tm3WnNo":16,"tm4WnNo":23,"tm5WnNo":24,"tm6WnNo":43,"bnsWnNo":9}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        DhLotteryClient.FetchOutcome outcome = client.fetchRound(1241);

        assertThat(outcome).isInstanceOf(DhLotteryClient.FetchOutcome.Unavailable.class);
    }
}
