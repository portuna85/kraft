package com.kraft.recommend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V20 마이그레이션의 실제 MariaDB 트리거로 {@link RecommendationHistoryImporter}가 반영한 이력의
 * version이 올라가고, {@link RecommendationHistoryProvider}가 이를 감지해 준비 완료로 전환되는지
 * 확인한다({@code MariaDbMigrationTest}와 같은 방식). H2 테스트({@link RecommendationHistoryImporterTest})는
 * Flyway를 실행하지 않으므로 트리거 자체는 검증하지 못한다.
 * <p>
 * 하나의 테스트 메서드에서 반영→정정 순서로 이어간다. {@link RecommendationHistoryProvider}는
 * 싱글턴 빈이라 캐시가 테스트 메서드 사이에 남는데, 각 테스트가 독립적으로 version을 0으로
 * 되돌리면(raw JDBC) 서로 다른 시나리오가 우연히 같은 version 값에 도달해 캐시가 잘못된
 * 스냅샷을 재사용할 수 있다(로컬 실측으로 확인). 하나로 합쳐 version이 단조 증가하는 하나의
 * 타임라인만 쓰면 이 충돌을 근본적으로 피할 수 있다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class RecommendationHistoryImportIntegrationTest {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.7.2");

    @Autowired
    private RecommendationHistoryImporter importer;

    @Autowired
    private RecommendationHistoryProvider provider;

    @Test
    @DisplayName("실제 트리거로 이력을 반영·정정하면 version이 매번 올라가고 이력이 준비 완료로 전환된다")
    void importingAndCorrectingHistory_bumpsVersionViaTrigger() {
        List<ImportedDraw> draws = List.of(
                new ImportedDraw(1, List.of(1, 2, 3, 4, 5, 6)),
                new ImportedDraw(2, List.of(7, 8, 9, 10, 11, 12)));
        importer.importHistory(draws, 2, "integration-test");

        var afterImport = provider.currentReadySnapshot();
        assertThat(afterImport.isReady()).isTrue();
        assertThat(afterImport.roundCount()).isEqualTo(2);
        assertThat(afterImport.verifiedThroughRound()).isEqualTo(2);
        // 트리거가 두 번(회차 2건 INSERT) 증가시키고, 메타데이터 갱신 자체도 한 번 더 올린다
        // (HIST-04/05 — updateVerificationMetadata가 항상 version을 +1 한다).
        assertThat(afterImport.version()).isEqualTo(3L);

        importer.importHistory(List.of(new ImportedDraw(1, List.of(2, 3, 4, 5, 6, 7))), 2, "integration-test-v2");

        var afterCorrection = provider.currentReadySnapshot();
        assertThat(afterCorrection.isReady()).isTrue();
        assertThat(afterCorrection.roundCount()).isEqualTo(2);
        // 정정 1건(UPDATE) + 메타데이터 갱신 1회만큼 또 올라가야 한다.
        assertThat(afterCorrection.version()).isEqualTo(5L);
        assertThat(afterCorrection.version()).isGreaterThan(afterImport.version());
    }
}
