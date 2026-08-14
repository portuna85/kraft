package com.kraft.common.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("수동 수집 end-to-end 타임아웃 계약")
class ManualCollectionTimeoutContractTest {

    @Test
    @DisplayName("백엔드 최악 재시도 시간보다 edge가 길고 브라우저는 edge보다 길다")
    void timeoutOrdering_isBackendThenEdgeThenClient() throws Exception {
        String externalClient = Files.readString(Path.of(
                "src/main/java/com/kraft/winningnumber/HttpExternalWinningNumberFetchClient.java"));
        String application = Files.readString(Path.of("src/main/resources/application.yml"));
        String caddy = Files.readString(Path.of("caddy/Caddyfile"));
        String webApi = Files.readString(Path.of("web/src/entities/ops/api.ts"));

        long connectMs = extract(externalClient, "connectTimeout\\(Duration\\.ofSeconds\\((\\d+)\\)\\)") * 1000;
        long readMs = extract(externalClient, "setReadTimeout\\(Duration\\.ofSeconds\\((\\d+)\\)\\)") * 1000;
        long attempts = extract(application,
                "externalLotto:[\\s\\S]*?max-attempts:\\s*(\\d+)");
        long waitMs = extract(application,
                "externalLotto:[\\s\\S]*?wait-duration:\\s*(\\d+)ms");
        long edgeMs = extract(caddy,
                "handle_path /ops-api/collect/\\* \\{[\\s\\S]*?response_header_timeout\\s+(\\d+)s") * 1000;
        long clientMs = extract(webApi, "const COLLECT_TIMEOUT_MS = ([\\d_]+);");
        long backendWorstCaseMs = attempts * (connectMs + readMs) + (attempts - 1) * waitMs;

        assertThat(backendWorstCaseMs).isLessThan(edgeMs);
        assertThat(edgeMs).isLessThan(clientMs);
        assertThat(edgeMs - backendWorstCaseMs).isGreaterThanOrEqualTo(3_000);
        assertThat(clientMs - edgeMs).isGreaterThanOrEqualTo(2_000);
    }

    private static long extract(String source, String regex) {
        var matcher = Pattern.compile(regex).matcher(source);
        assertThat(matcher.find()).as("pattern %s", regex).isTrue();
        return Long.parseLong(matcher.group(1).replace("_", ""));
    }
}
