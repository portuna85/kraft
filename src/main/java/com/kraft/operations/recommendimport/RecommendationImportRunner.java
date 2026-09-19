package com.kraft.operations.recommendimport;

import com.kraft.recommend.domain.RecommendationImportException;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code recommend-import} 프로파일로 앱을 띄우면 검증된 당첨 이력 CSV 파일 하나를 한 번
 * 반영하고 종료한다.
 *
 * <pre>
 * java -jar kraft.jar --spring.profiles.active=recommend-import \
 *   --app.recommend.import.file=/path/to/verified-history.csv \
 *   --app.recommend.import.verified-through-round=1234 \
 *   --app.recommend.import.source-reference="..."
 * </pre>
 *
 * CSV는 헤더 없이 한 줄에 한 회차: {@code round_no,n1,n2,n3,n4,n5,n6}. 실제 당첨 데이터의
 * 출처·최신성 확인은 이 도구의 책임이 아니다 — 호출자가 이미 검증한 자료만 넘겨야 한다
 * (HIST-08, docs/03-number-recommendation-policy.md).
 * <p>
 * 전용 프로파일로 가둔 이유는 {@link com.kraft.operations.rekey.EmailRekeyRunner}와 같다 —
 * {@code local}·{@code prod} 정상 기동 경로에는 이 빈이 활성화될 경로가 없다. 끝나면 종료
 * 코드를 남기고 내려간다(성공 0, 실패 1).
 */
@Slf4j
@RequiredArgsConstructor
@Profile("recommend-import")
@Component
public class RecommendationImportRunner implements ApplicationRunner {

    private final RecommendationHistoryImporter importer;
    private final ConfigurableApplicationContext context;

    @Value("${app.recommend.import.file}")
    private String filePath;

    @Value("${app.recommend.import.verified-through-round}")
    private int verifiedThroughRound;

    @Value("${app.recommend.import.source-reference}")
    private String sourceReference;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = importHistory();
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    private int importHistory() {
        try {
            List<ImportedDraw> draws = parse(Path.of(filePath));
            RecommendationHistoryImporter.Result result =
                    importer.importHistory(draws, verifiedThroughRound, sourceReference);

            log.info("이력 반영 완료. 신규 {}건, 정정 {}건, 검증 기준 회차 {}.",
                    result.inserted(), result.updated(), result.verifiedThroughRound());
            return 0;
        } catch (RecommendationImportException e) {
            log.error("이력 반영 검증에 실패했습니다 [{}]: {}", e.getReason(), e.getMessage());
            return 1;
        } catch (IOException e) {
            log.error("이력 파일을 읽을 수 없습니다: {}", filePath, e);
            return 1;
        } catch (Exception e) {
            log.error("이력 반영 중 예상하지 못한 오류가 발생했습니다.", e);
            return 1;
        }
    }

    private List<ImportedDraw> parse(Path path) throws IOException {
        List<ImportedDraw> draws = new ArrayList<>();
        List<String> lines = Files.readAllLines(path);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(",");
            if (parts.length != 7) {
                throw new RecommendationImportException("INVALID_CSV_LINE",
                        "형식은 round_no,n1,n2,n3,n4,n5,n6 이어야 합니다: " + line);
            }
            try {
                int roundNo = Integer.parseInt(parts[0].trim());
                List<Integer> numbers = new ArrayList<>();
                for (int i = 1; i <= 6; i++) {
                    numbers.add(Integer.parseInt(parts[i].trim()));
                }
                draws.add(new ImportedDraw(roundNo, numbers));
            } catch (NumberFormatException e) {
                throw new RecommendationImportException("INVALID_CSV_LINE",
                        "숫자로 파싱할 수 없는 줄입니다: " + line);
            }
        }
        return draws;
    }
}
