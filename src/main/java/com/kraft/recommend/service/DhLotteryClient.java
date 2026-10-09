package com.kraft.recommend.service;

import com.kraft.recommend.domain.DrawDetails;
import com.kraft.recommend.domain.LottoNumbers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 동행복권 추첨결과 화면이 내부적으로 쓰는 비공식 주소({@code lt645/selectPstLt645InfoNew.do}) 하나를 부르는
 * 얇은 클라이언트. 공식 API가 아니라 응답 형식이 예고 없이 바뀔 수 있다. DB·트랜잭션은 모른다.
 * <p>
 * 이 주소는 요청한 회차를 포함한 최근 10개 회차를 한 번에 주므로, 마지막 배치를 {@code cache}에 담아 순차
 * 조회(백필) 때 요청 수를 줄인다. 결과는 예외가 아니라 {@link FetchOutcome}으로 구분한다(추첨 전 vs 신뢰할 수
 * 없는 응답).
 * <p>
 * 연속 실패 서킷은 두지 않는다 — 호출자({@link RecommendationAutoFetchScheduler}, 백필 러너)가 이미
 * {@code Unavailable}에서 그 실행을 멈춘다.
 */
@Slf4j
@Component
public class DhLotteryClient {

    private static final String BASE_URL = "https://www.dhlottery.co.kr";
    private static final String PATH = "/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd={drwNo}";

    /**
     * 이 서비스를 식별할 수 있는 User-Agent(위장이 아니라 문제 시 상대가 연락할 수 있게). ASCII만 쓴다 —
     * 비ASCII 헤더는 상대 방화벽이 거부해 500이 돌아온다.
     */
    static final String USER_AGENT = "kraft-recommend-bot/1.0 (+https://kraft.io.kr; lottery round lookup for number recommendation, scheduled up to 4 times a week)";

    private final RestClient restClient;
    /** 마지막으로 받은 배치. 채우는 도중이 보이지 않게 {@code volatile} 참조를 통째로 교체한다. */
    private volatile Map<Integer, ImportedDraw> cache = Map.of();

    @Autowired
    public DhLotteryClient(
            @Value("${app.recommend.dhlottery.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${app.recommend.dhlottery.read-timeout-ms:10000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        // RestClient.Builder 빈은 starter-restclient가 있어야 하므로 직접 만든다.
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    /** 테스트 전용 — 목 서버에 바인딩된 {@link RestClient}를 그대로 쓴다(requestFactory를 덮어쓰면 바인딩이 사라진다). */
    DhLotteryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** 회차 하나를 조회한다. 예외를 던지지 않고 모든 실패를 {@link FetchOutcome.Unavailable}로 흡수한다. */
    public FetchOutcome fetchRound(int drwNo) {
        ImportedDraw cached = cache.get(drwNo);
        if (cached != null) {
            return new FetchOutcome.Success(cached);
        }

        DhLotteryBatchResponse response;
        try {
            response = restClient.get()
                    .uri(PATH, drwNo)
                    .retrieve()
                    .body(DhLotteryBatchResponse.class);
        } catch (RestClientException e) {
            log.warn("동행복권 회차 {} 조회 실패(전송 오류): {}", drwNo, e.getMessage());
            return new FetchOutcome.Unavailable("HTTP_ERROR: " + e.getMessage());
        } catch (Exception e) {
            // 홈페이지 리다이렉트 등 JSON이 아닌 응답은 메시지 컨버터가 여기서 실패한다.
            log.warn("동행복권 회차 {} 조회 실패(응답을 JSON으로 해석할 수 없음): {}", drwNo, e.getMessage());
            return new FetchOutcome.Unavailable("UNPARSEABLE_RESPONSE: " + e.getMessage());
        }

        List<DhLotteryDrawItem> items = response == null || response.data() == null
                ? List.of()
                : response.data().list();
        if (items == null) {
            items = List.of();
        }

        Map<Integer, ImportedDraw> nextBatch = new HashMap<>();
        boolean requestedRoundExists = false;
        for (DhLotteryDrawItem item : items) {
            if (item.ltEpsd() == drwNo) {
                requestedRoundExists = true;
            }
            try {
                // 번호는 nullable이라 List.of가 NPE를 던진다 — 한 행이 배치 전체를 깨지 않게 try 안에서 잡는다.
                List<Integer> numbers = new ArrayList<>(List.of(
                        item.tm1WnNo(), item.tm2WnNo(), item.tm3WnNo(),
                        item.tm4WnNo(), item.tm5WnNo(), item.tm6WnNo()));
                LottoNumbers.of(numbers);
                nextBatch.put(item.ltEpsd(), new ImportedDraw(item.ltEpsd(), numbers, buildDetails(item)));
            } catch (RuntimeException e) {
                log.warn("동행복권 회차 {} 응답의 번호가 유효하지 않음: {}", item.ltEpsd(), e.getMessage());
            }
        }
        // 다 채운 뒤 한 번에 교체한다.
        cache = Map.copyOf(nextBatch);

        if (!requestedRoundExists) {
            return new FetchOutcome.NotYetDrawn();
        }
        ImportedDraw draw = cache.get(drwNo);
        if (draw == null) {
            return new FetchOutcome.Unavailable("INVALID_NUMBERS: round=" + drwNo);
        }
        return new FetchOutcome.Success(draw);
    }

    /** 화면 표시용 부가 정보. 이상한 필드는 null로 두고 나머지는 살린다(본번호 반영을 막지 않는다). */
    private DrawDetails buildDetails(DhLotteryDrawItem item) {
        Integer bonus = item.bnsWnNo();
        if (bonus != null && (bonus < LottoNumbers.MIN || bonus > LottoNumbers.MAX)) {
            log.warn("동행복권 회차 {} 보너스 번호가 범위를 벗어남: {}", item.ltEpsd(), bonus);
            bonus = null;
        }
        LocalDate drawDate = null;
        if (item.ltRflYmd() != null) {
            try {
                drawDate = LocalDate.parse(item.ltRflYmd(), DateTimeFormatter.BASIC_ISO_DATE);
            } catch (RuntimeException e) {
                log.warn("동행복권 회차 {} 추첨일 형식을 해석할 수 없음: {}", item.ltEpsd(), item.ltRflYmd());
            }
        }
        return new DrawDetails(bonus, drawDate, item.rnk1WnNope(), item.rnk1WnAmt());
    }

    /** {@code data.list}만 쓴다 — {@code resultCode}·{@code resultMessage}는 성공 시 비어 있다. */
    record DhLotteryBatchResponse(String resultCode, String resultMessage, DhLotteryBatchData data) {
    }

    record DhLotteryBatchData(List<DhLotteryDrawItem> list) {
    }

    /** 필요한 필드만 매핑한다. 보너스·추첨일(yyyyMMdd)·1등 당첨자 수/금액은 표시 전용({@link DrawDetails})이다. */
    record DhLotteryDrawItem(
            int ltEpsd,
            Integer tm1WnNo, Integer tm2WnNo, Integer tm3WnNo,
            Integer tm4WnNo, Integer tm5WnNo, Integer tm6WnNo,
            Integer bnsWnNo, String ltRflYmd, Integer rnk1WnNope, Long rnk1WnAmt) {
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
