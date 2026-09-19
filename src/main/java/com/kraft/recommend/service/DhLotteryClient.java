package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoNumbers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 동행복권의 비공식·내부용 회차 조회 주소 하나만 알고 있는 얇은 클라이언트. 공식 문서화된
 * API가 아니다 — 응답 형식이 예고 없이 바뀌거나, 자동화된 접근이 차단될 수 있다(실제로 이
 * 기능을 설계하며 {@code drwNo=1241}을 요청했을 때 JSON 대신 봇 차단 대기실 HTML을 받은
 * 사례가 있다). 그래서 이 클래스는 "성공"과 "실패"를 예외가 아니라 {@link FetchOutcome}로
 * 명시적으로 구분해, 호출자가 실패 종류(아직 추첨 전 vs 신뢰할 수 없는 응답)를 혼동하지
 * 않게 한다.
 * <p>
 * DB·트랜잭션을 전혀 모른다 — 회차 하나를 조회하는 것만 담당하며,
 * {@link RecommendationAutoFetchScheduler}와 백필 러너가 함께 재사용한다.
 */
@Slf4j
@Component
public class DhLotteryClient {

    private static final String BASE_URL = "https://www.dhlottery.co.kr";
    private static final String PATH = "/common.do?method=getLottoNumber&drwNo={drwNo}";

    private final RestClient restClient;

    @Autowired
    public DhLotteryClient(
            @Value("${app.recommend.dhlottery.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${app.recommend.dhlottery.read-timeout-ms:10000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        // RestClient.Builder는 spring-boot-starter-restclient가 있어야 자동 구성되는 빈이라 이
        // 프로젝트 의존성에는 없다 — 새 의존성을 추가하는 대신 직접 만든다(HTTP 클라이언트
        // 하나만 필요한 이 용도에는 충분하다).
        this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
    }

    /**
     * 테스트 전용 — 이미 구성된(예: {@code MockRestServiceServer}에 바인딩된) {@link RestClient}를
     * 그대로 쓴다. 위 생성자처럼 타임아웃용 {@code requestFactory}를 다시 덮어쓰면 목 서버 바인딩이
     * 사라지므로, 이 경로는 그런 재설정을 하지 않는다.
     */
    DhLotteryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 지정한 회차 하나를 조회한다. 이 메서드는 예외를 던지지 않는다(전송 오류·비-JSON 응답·
     * 검증 실패를 모두 {@link FetchOutcome.Unavailable}로 흡수한다) — 호출자가 매번
     * try/catch 없이 세 가지 결과만 분기하면 되게 하기 위함이다.
     */
    public FetchOutcome fetchRound(int drwNo) {
        DhLotteryResponse response;
        try {
            response = restClient.get()
                    .uri(PATH, drwNo)
                    .retrieve()
                    .body(DhLotteryResponse.class);
        } catch (RestClientException e) {
            log.warn("동행복권 회차 {} 조회 실패(전송 오류): {}", drwNo, e.getMessage());
            return new FetchOutcome.Unavailable("HTTP_ERROR: " + e.getMessage());
        } catch (Exception e) {
            // 봇 차단 대기실처럼 JSON이 아닌 응답(HTML 등)이 오면 메시지 컨버터가 여기서 실패한다.
            log.warn("동행복권 회차 {} 조회 실패(응답을 JSON으로 해석할 수 없음): {}", drwNo, e.getMessage());
            return new FetchOutcome.Unavailable("UNPARSEABLE_RESPONSE: " + e.getMessage());
        }

        if (response == null || !"success".equals(response.returnValue())) {
            return new FetchOutcome.NotYetDrawn();
        }
        if (!Integer.valueOf(drwNo).equals(response.drwNo())) {
            log.warn("동행복권 회차 {} 조회 응답의 회차가 일치하지 않음: {}", drwNo, response.drwNo());
            return new FetchOutcome.Unavailable("ROUND_MISMATCH: requested=" + drwNo + " got=" + response.drwNo());
        }

        List<Integer> numbers = new ArrayList<>(List.of(
                response.drwtNo1(), response.drwtNo2(), response.drwtNo3(),
                response.drwtNo4(), response.drwtNo5(), response.drwtNo6()));
        try {
            LottoNumbers.of(numbers);
        } catch (RuntimeException e) {
            log.warn("동행복권 회차 {} 응답의 번호가 유효하지 않음: {}", drwNo, e.getMessage());
            return new FetchOutcome.Unavailable("INVALID_NUMBERS: " + e.getMessage());
        }

        return new FetchOutcome.Success(new ImportedDraw(drwNo, numbers));
    }

    /** {@code returnValue}가 "success"가 아닌 나머지 필드는 신뢰하지 않는다. */
    record DhLotteryResponse(
            String returnValue,
            Integer drwNo,
            Integer drwtNo1, Integer drwtNo2, Integer drwtNo3,
            Integer drwtNo4, Integer drwtNo5, Integer drwtNo6) {
    }

    public sealed interface FetchOutcome {

        record Success(ImportedDraw draw) implements FetchOutcome {
        }

        /** JSON 응답은 받았지만 아직 추첨 전(또는 존재하지 않는 회차)이라는 뜻. */
        record NotYetDrawn() implements FetchOutcome {
        }

        /** 전송 오류·봇 차단 등 비-JSON 응답·회차 불일치·번호 검증 실패 등, 신뢰할 수 없는 응답. */
        record Unavailable(String reason) implements FetchOutcome {
        }
    }
}
