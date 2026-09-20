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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 동행복권의 비공식·내부용 회차 조회 주소 하나만 알고 있는 얇은 클라이언트. 공식 문서화된
 * API가 아니다 — 응답 형식이 예고 없이 바뀔 수 있다(실제로 예전에 쓰던
 * {@code common.do?method=getLottoNumber}는 어느 시점부터 요청과 무관하게 홈페이지로
 * 302 리다이렉트만 돌려주게 되었다 — curl과 실제 브라우저 세션 모두에서 재현 확인).
 * 지금은 로또6/45 추첨결과 화면(신규 SPA)이 내부적으로 쓰는
 * {@code lt645/selectPstLt645InfoNew.do}를 대신 쓴다.
 * <p>
 * 이 주소는 회차 하나가 아니라 요청한 회차를 포함한 최근 10개 회차를 한 번에 돌려준다
 * (범위를 벗어나면 1회차 쪽으로 잘림). 그래서 {@link #fetchRound(int)}는 매번 새로 HTTP
 * 요청을 보내는 대신, 마지막으로 받은 배치를 {@code cache}에 담아 두고 이미 받아 온 회차는
 * 캐시에서 바로 돌려준다 — 백필처럼 회차를 순차로 조회할 때 실제 요청 수를 최대 1/10로
 * 줄이기 위함이다. 새 배치를 받으면 이전 캐시는 버린다(이 클래스가 필요한 건 "다음에 조회할
 * 회차"뿐이라 전체 이력을 누적해 들고 있을 이유가 없다).
 * <p>
 * 그래도 "성공"과 "실패"는 예외가 아니라 {@link FetchOutcome}로 명시적으로 구분해, 호출자가
 * 실패 종류(아직 추첨 전 vs 신뢰할 수 없는 응답)를 혼동하지 않게 한다.
 * <p>
 * DB·트랜잭션을 전혀 모른다 — 회차 하나를 조회하는 것만 담당하며,
 * {@link RecommendationAutoFetchScheduler}와 백필 러너가 함께 재사용한다.
 */
@Slf4j
@Component
public class DhLotteryClient {

    private static final String BASE_URL = "https://www.dhlottery.co.kr";
    private static final String PATH = "/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd={drwNo}";

    private final RestClient restClient;
    private final Map<Integer, ImportedDraw> cache = new ConcurrentHashMap<>();

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
            // 홈페이지로 리다이렉트되는 등 JSON이 아닌 응답(HTML 등)이 오면 메시지 컨버터가
            // 여기서 실패한다.
            log.warn("동행복권 회차 {} 조회 실패(응답을 JSON으로 해석할 수 없음): {}", drwNo, e.getMessage());
            return new FetchOutcome.Unavailable("UNPARSEABLE_RESPONSE: " + e.getMessage());
        }

        List<DhLotteryDrawItem> items = response == null || response.data() == null
                ? List.of()
                : response.data().list();
        if (items == null) {
            items = List.of();
        }

        cache.clear();
        boolean requestedRoundExists = false;
        for (DhLotteryDrawItem item : items) {
            if (item.ltEpsd() == drwNo) {
                requestedRoundExists = true;
            }
            List<Integer> numbers = new ArrayList<>(List.of(
                    item.tm1WnNo(), item.tm2WnNo(), item.tm3WnNo(),
                    item.tm4WnNo(), item.tm5WnNo(), item.tm6WnNo()));
            try {
                LottoNumbers.of(numbers);
                cache.put(item.ltEpsd(), new ImportedDraw(item.ltEpsd(), numbers, buildDetails(item)));
            } catch (RuntimeException e) {
                log.warn("동행복권 회차 {} 응답의 번호가 유효하지 않음: {}", item.ltEpsd(), e.getMessage());
            }
        }

        if (!requestedRoundExists) {
            return new FetchOutcome.NotYetDrawn();
        }
        ImportedDraw draw = cache.get(drwNo);
        if (draw == null) {
            return new FetchOutcome.Unavailable("INVALID_NUMBERS: round=" + drwNo);
        }
        return new FetchOutcome.Success(draw);
    }

    /**
     * 화면 표시용 부가 정보({@link DrawDetails})를 만든다. 개별 필드가 이상해도(범위 밖
     * 보너스 번호, 해석 안 되는 날짜) 그 필드만 null로 남기고 나머지는 살린다 — 부가 정보
     * 하나가 깨졌다고 본번호 6개 이력 반영까지 막을 이유가 없다.
     */
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

    /**
     * 당첨자 인원수 이상의 등수별 상세(2등 이하, 누적 판매액 등)는 이 기능에 필요 없어 매핑하지
     * 않는다(알 수 없는 필드는 무시됨). {@code bnsWnNo}(보너스 번호)·{@code ltRflYmd}(추첨일,
     * yyyyMMdd)·{@code rnk1WnNope}(1등 당첨자 수)·{@code rnk1WnAmt}(1등 1인당 당첨금)는
     * 화면 표시 전용 부가 정보({@link DrawDetails})로만 쓰인다.
     */
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
