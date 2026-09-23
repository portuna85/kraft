package com.kraft.operations.recommendbackfill;

import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.RecommendationImportException;
import com.kraft.recommend.service.DhLotteryClient;
import com.kraft.recommend.service.ImportedDraw;
import com.kraft.recommend.service.RecommendationHistoryImporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code recommend-backfill} 프로파일로 앱을 띄우면 동행복권에서 1회부터(또는 이미 반영된
 * 다음 회차부터) 지정한 회차까지 순차적으로 조회해 반영하고 종료한다. 최초 이력이 없는
 * 상태에서 한 번, 또는 자동 수집이 한동안 실패해 공백이 생겼을 때 다시 쓴다.
 *
 * <pre>
 * java -jar kraft.jar --spring.profiles.active=prod,recommend-backfill \
 *   --app.recommend.backfill.up-to-round=1241 \
 *   --app.recommend.backfill.chunk-size=50 \
 *   --app.recommend.backfill.request-delay-ms=300
 * </pre>
 * {@code prod}를 함께 켜야 데이터소스가 잡힌다 — {@code application-recommend-backfill.yml}은
 * 이 실행 동안 다른 주기 작업을 끄는 것만 담당한다(개선 보고서 OBS-05).
 *
 * 동행복권의 회차 조회 주소는 비공식·내부용이라 중간에 봇 차단 등으로 막힐 수 있다. 그래서
 * 전체를 한 트랜잭션으로 묶지 않고 {@code chunk-size} 회차씩 끊어 커밋한다 — 막히더라도
 * 이미 확인된 구간은 남아 재실행 시 그 다음 회차부터 이어간다. 요청 사이에는
 * {@code request-delay-ms}만큼 쉬어 한 사이트에 부담을 주지 않는다.
 * <p>
 * {@code --app.recommend.backfill.dry-run=true}를 주면 조회만 하고 어떤 청크도 커밋하지
 * 않는다 — 연결·응답 형식을 미리 확인하는 용도다.
 * <p>
 * 전용 프로파일로 가둔 이유는 {@link com.kraft.operations.rekey.EmailRekeyRunner}와 같다.
 * 끝나면 종료 코드를 남기고 내려간다 — 요청한 회차까지 모두 채웠으면 0, 중간에 멈췄으면 1
 * (실패가 아니라 "다시 실행하라"는 신호다. 재실행하면 남은 회차부터 자동으로 이어간다).
 */
@Slf4j
@RequiredArgsConstructor
@Profile("recommend-backfill")
@Component
public class RecommendationBackfillRunner implements ApplicationRunner {

    private final DhLotteryClient dhLotteryClient;
    private final RecommendationHistoryImporter importer;
    private final RecommendationHistoryStateRepository stateRepository;
    private final ConfigurableApplicationContext context;

    @Value("${app.recommend.backfill.up-to-round}")
    private int upToRound;

    @Value("${app.recommend.backfill.chunk-size:50}")
    private int chunkSize;

    @Value("${app.recommend.backfill.request-delay-ms:300}")
    private long requestDelayMs;

    @Value("${app.recommend.backfill.dry-run:false}")
    private boolean dryRun;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = backfill();
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /** package-private: 테스트가 {@code System.exit}를 거치지 않고 종료 코드만 직접 확인한다. */
    int backfill() {
        // 잘못된 인자로 몇 시간짜리 백필을 돌리다 뒤늦게 실패를 알아채는 일이 없게, 시작 전에
        // 검사한다(개선 보고서 OBS-05).
        if (chunkSize <= 0) {
            log.error("chunk-size는 1 이상이어야 합니다. 입력값={}", chunkSize);
            return 1;
        }
        if (requestDelayMs < 0) {
            log.error("request-delay-ms는 0 이상이어야 합니다. 입력값={}", requestDelayMs);
            return 1;
        }

        int from = stateRepository.findById(1)
                .map(state -> state.getVerifiedThroughRound() == null ? 0 : state.getVerifiedThroughRound())
                .orElse(0) + 1;

        if (from > upToRound) {
            log.info("이미 회차 {}까지 반영되어 있습니다(요청 상한 {}). 할 일이 없습니다.", from - 1, upToRound);
            return 0;
        }

        log.info("회차 {}부터 {}까지 백필을 시작합니다(청크 {}회차, 요청 간격 {}ms, dry-run={}).",
                from, upToRound, chunkSize, requestDelayMs, dryRun);

        List<ImportedDraw> chunk = new ArrayList<>();
        for (int round = from; round <= upToRound; round++) {
            DhLotteryClient.FetchOutcome outcome = dhLotteryClient.fetchRound(round);

            if (outcome instanceof DhLotteryClient.FetchOutcome.Success success) {
                chunk.add(success.draw());
            } else {
                String reason = describe(outcome);
                if (!chunk.isEmpty() && !commitChunk(chunk, round - 1)) {
                    return 1;
                }
                log.warn("회차 {}에서 멈췄습니다({}). 요청한 상한({})까지 채우지 못했습니다. "
                                + "원인을 확인한 뒤 같은 명령으로 다시 실행하면 회차 {}부터 이어갑니다.",
                        round, reason, upToRound, round);
                return 1;
            }

            boolean chunkFull = chunk.size() >= chunkSize;
            boolean lastRound = round == upToRound;
            if ((chunkFull || lastRound) && !commitChunk(chunk, round)) {
                return 1;
            }

            if (!sleep(requestDelayMs)) {
                log.warn("대기 중 인터럽트를 받아 백필을 중단합니다. 회차 {}까지는 이미 반영되었습니다. "
                                + "같은 명령으로 다시 실행하면 이어갑니다.", round);
                return 1;
            }
        }

        log.info("백필 완료. 회차 {}까지 반영했습니다.", upToRound);
        return 0;
    }

    /** 커밋 성공 시 {@code chunk}를 비우고 true, 검증 실패로 아무것도 쓰지 못했으면 false. */
    private boolean commitChunk(List<ImportedDraw> chunk, int verifiedThroughRound) {
        if (chunk.isEmpty()) {
            return true;
        }
        if (dryRun) {
            log.info("[dry-run] 회차 {}까지 {}건 조회 확인. 커밋하지 않습니다.", verifiedThroughRound, chunk.size());
            chunk.clear();
            return true;
        }
        try {
            RecommendationHistoryImporter.Result result =
                    importer.importHistory(List.copyOf(chunk), verifiedThroughRound, "dhlottery-api-backfill");
            log.info("회차 {}까지 청크 반영 완료(신규 {}건, 정정 {}건).",
                    verifiedThroughRound, result.inserted(), result.updated());
            chunk.clear();
            return true;
        } catch (RecommendationImportException e) {
            log.error("회차 {}까지 청크 반영에 실패했습니다 [{}]: {}", verifiedThroughRound, e.getReason(), e.getMessage());
            return false;
        }
    }

    private String describe(DhLotteryClient.FetchOutcome outcome) {
        return switch (outcome) {
            case DhLotteryClient.FetchOutcome.NotYetDrawn ignored -> "아직 추첨 전";
            case DhLotteryClient.FetchOutcome.Unavailable unavailable -> unavailable.reason();
            case DhLotteryClient.FetchOutcome.Success ignored -> "success"; // 도달하지 않음
        };
    }

    /**
     * @return 정상적으로 다 쉬었으면(또는 쉴 필요가 없었으면) true, 인터럽트로 중단됐으면 false.
     * 예전에는 인터럽트를 받아도 플래그만 다시 세우고 반복문을 계속 돌았다 — 종료 신호(예:
     * 배포 중 프로세스 강제 종료)를 받고도 다음 회차 요청을 계속 내보냈다(개선 보고서 OBS-05).
     */
    private boolean sleep(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
