package com.kraft.common.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Prometheus 알림 런북 참조 계약")
class AlertRunbookContractTest {

    private static final Path RULES = Path.of("infra/prometheus/rules/kraft_alerts.yml");
    private static final Pattern SCRIPT_REFERENCE = Pattern.compile("scripts/[a-z0-9_./-]+\\.sh");

    @Test
    @DisplayName("알림에서 언급하는 저장소 스크립트가 모두 실제로 존재한다")
    void referencedScripts_exist() throws Exception {
        String rules = Files.readString(RULES);
        var matcher = SCRIPT_REFERENCE.matcher(rules);

        while (matcher.find()) {
            assertThat(Path.of(matcher.group())).as("runbook script %s", matcher.group()).exists();
        }
    }

    @Test
    @DisplayName("추천 이력 런북은 실제 메트릭명과 실행 가능한 복구 절차만 사용한다")
    void recommendationHistoryRunbook_usesRegisteredMetricAndRecoveryAction() throws Exception {
        String rules = Files.readString(RULES);
        String recommendationService = Files.readString(
                Path.of("src/main/java/com/kraft/recommend/LottoRecommendationService.java"));

        assertThat(recommendationService).contains("kraft_lotto_first_missing_round");
        assertThat(rules)
                .contains("kraft_lotto_first_missing_round")
                .doesNotContain("kraft_lotto_history_first_missing_round", "POST /ops/collect 또는");
        assertThat(rules).contains("docker compose -f docker-compose.prod.yml restart backend");
    }

    @Test
    @DisplayName("수동 수집 런북 경로는 실제 OpsController 매핑과 일치한다")
    void collectionRunbook_usesExistingOpsRoute() throws Exception {
        String rules = Files.readString(RULES);
        String opsController = Files.readString(Path.of("src/main/java/com/kraft/ops/OpsController.java"));

        assertThat(rules).contains("POST /ops/collect/latest");
        assertThat(opsController).contains("@PostMapping(\"/collect/latest\")");
        assertThat(rules).doesNotContain("summary rebuild in /ops", "scripts/server collection scheduler");
    }
}
