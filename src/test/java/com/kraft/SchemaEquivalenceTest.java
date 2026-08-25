package com.kraft;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * QA-BE-01(docs/improvement.md): 기본 테스트 스위트(123개 중 106개)는 Flyway가 아니라
 * Hibernate {@code ddl-auto: create-drop}(H2) 위에서 돈다. 그 스키마가 실제 프로덕션
 * 스키마(Flyway)와 갈라지면 — DB-MAP-01의 {@code content varchar(255)} vs {@code TEXT}가
 * 그랬듯 — 기본 스위트가 절대 잡을 수 없는 버그가 생긴다.
 *
 * <p>이 테스트는 같은 MariaDB 컨테이너 안에 두 개의 독립된 스키마(데이터베이스)를 만든다.
 * 하나는 Flyway로(프로덕션과 같은 경로), 하나는 Hibernate {@code hibernate.hbm2ddl.auto=create}
 * (테스트 기본 스위트가 실제로 쓰는 경로 — H2 대신 MariaDB로 재현해 방언 차이 없이 순수하게
 * "두 스키마 생성 경로의 컬럼 타입"만 비교한다)로 만든 뒤, {@code information_schema.columns}의
 * {@code DATA_TYPE}을 테이블·컬럼별로 대조한다.
 *
 * <p>발산을 실패시키기보다 {@code KNOWN_DIVERGENCES} 허용 목록으로 관리한다 — 문서가
 * 명시한 대로다. 새 발산이 생기면(엔티티 매핑에 columnDefinition을 안 쓰는 실수 등) 이
 * 테스트가 실패해 검토를 강제하고, 검토 후 의도된 것이면 목록에 추가한다.
 */
@Testcontainers
@DisplayName("Flyway 스키마와 Hibernate create-drop 스키마의 컬럼 타입 동등성 테스트")
class SchemaEquivalenceTest {

    /**
     * {@code "테이블.컬럼: flyway=X hbm=Y"} 형태 — 값 문자열은
     * {@link #columnTypesMatchBetweenFlywayAndHibernateCreateDrop()}가 실제로 만드는
     * 형식과 정확히 같아야 한다(신규 발산 발견 시 테스트 실패 메시지에서 그대로 복사).
     * 이 12개는 이 테스트를 처음 만들며 실측으로 발견한 기존 발산이다 — DB-MAP-01의
     * {@code content}처럼 새로 생긴 문제가 아니라 전부 이미 운영 중인 스키마다.
     */
    private static final Set<String> KNOWN_DIVERGENCES = Set.of(
            // boolean 필드를 columnDefinition 없이 매핑하면 Hibernate/MariaDB 방언은 BIT(1)을
            // 만드는데, Flyway DDL은 TINYINT(1)이다(V7/V16). 둘 다 1비트 불리언의 표준 MySQL/
            // MariaDB 표현이라 기능적으로 동등하다 — JDBC 드라이버가 양쪽 다 boolean으로 읽는다.
            "admin_users.enabled: flyway=tinyint hbm=bit",
            "community_comments.deleted: flyway=tinyint hbm=bit",
            // @Enumerated(STRING) Java enum을 Hibernate가 MariaDB 방언에서 DDL을 새로 생성하면
            // 네이티브 ENUM(...) 컬럼을 만든다. Flyway DDL은 varchar다(V15/V21/V26/V29 등,
            // 값 목록을 Java enum이 아니라 애플리케이션 레벨 CHECK/검증으로 관리하는 이 저장소의
            // 관례). ddl-auto: validate는 이 차이를 허용한다(실제 prod가 이 조합으로 무사고
            // 운영 중) — validate가 ENUM DDL을 강제하지 않기 때문에 실무 영향은 없다.
            "community_posts.category: flyway=varchar hbm=enum",
            "community_posts.status: flyway=varchar hbm=enum",
            "community_reports.reason: flyway=varchar hbm=enum",
            "community_reports.target_type: flyway=varchar hbm=enum",
            "winning_number_operation_logs.execution_status: flyway=varchar hbm=enum",
            "winning_number_operation_logs.operation_type: flyway=varchar hbm=enum",
            // 고정 길이 SHA-256 hex(64자)를 Flyway는 CHAR(64)로 선언하지만(V1/V13/V19/V28),
            // 엔티티는 @Column(length = 64)만 써서 Hibernate는 VARCHAR(64)를 만든다. 저장·비교
            // 결과는 동일하다(둘 다 64바이트 고정폭 문자열) — CHAR가 디스크를 약간 덜 쓰는
            // 최적화 차이일 뿐 정확성에 영향 없다.
            "device_claims.device_token_hash: flyway=char hbm=varchar",
            "recommendation_sets.client_token_hash: flyway=char hbm=varchar",
            "saved_number_client_locks.client_token_hash: flyway=char hbm=varchar",
            "saved_numbers.client_token_hash: flyway=char hbm=varchar");

    /**
     * JPA 엔티티가 아니라 비교 대상이 아닌 테이블. {@code flyway_schema_history}는 Flyway가
     * 직접 관리하고, {@code shedlock}은 ShedLock 라이브러리가 자기 SQL로 만든다 — 둘 다
     * Hibernate 스키마 생성 경로를 거치지 않으므로 "hbm에 없음"은 발산이 아니라 당연하다.
     */
    private static final Set<String> IGNORED_TABLES = Set.of("flyway_schema_history", "shedlock");

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.7")
            .withDatabaseName("kraft_flyway")
            .withUsername("test")
            .withPassword("test");

    private static SessionFactory hibernateSessionFactory;

    @BeforeAll
    static void setUpSchemas() throws Exception {
        Flyway.configure()
                .dataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())
                .load()
                .migrate();

        String hbmDatabase = "kraft_hbm";
        // MariaDBContainer의 test 유저는 컨테이너 생성 시 지정한 데이터베이스에만 권한이
        // 있다 — 새로 만드는 두 번째 데이터베이스는 root로 만들고 권한을 내려줘야 한다.
        try (Connection connection = DriverManager.getConnection(
                mariadb.getJdbcUrl(), "root", mariadb.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + hbmDatabase);
            statement.execute("GRANT ALL PRIVILEGES ON " + hbmDatabase + ".* TO '"
                    + mariadb.getUsername() + "'@'%'");
        }

        String hbmJdbcUrl = mariadb.getJdbcUrl().replace(
                "/" + mariadb.getDatabaseName(), "/" + hbmDatabase);

        // Spring Boot의 HibernateProperties가 기본으로 적용하는 두 네이밍 전략과 똑같이
        // 맞춘다 — 안 맞추면 raw Hibernate 기본 전략(camelCase 그대로)이 쓰여
        // adminUser/createdAt 같은 카멜케이스 필드가 admin_user/created_at으로 변환되지
        // 않아 실제로는 없는 "발산"이 대량으로 잡히는 것을 실측으로 확인했다.
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("jakarta.persistence.jdbc.url", hbmJdbcUrl)
                .applySetting("jakarta.persistence.jdbc.user", mariadb.getUsername())
                .applySetting("jakarta.persistence.jdbc.password", mariadb.getPassword())
                .applySetting("hibernate.hbm2ddl.auto", "create")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
                .build();

        MetadataSources sources = new MetadataSources(registry);
        for (Class<?> entityClass : scanEntityClasses()) {
            sources.addAnnotatedClass(entityClass);
        }
        Metadata metadata = sources.buildMetadata();
        // buildSessionFactory 자체가 hibernate.hbm2ddl.auto=create를 실행해 스키마를
        // 만든다 — 기본 테스트 스위트가 컨텍스트를 띄울 때 벌어지는 일과 정확히 같다.
        hibernateSessionFactory = metadata.buildSessionFactory();
    }

    @AfterAll
    static void tearDown() {
        if (hibernateSessionFactory != null) {
            hibernateSessionFactory.close();
        }
    }

    @Test
    @DisplayName("Flyway와 Hibernate create-drop이 만든 컬럼 타입이 일치한다(허용 목록 제외)")
    void columnTypesMatchBetweenFlywayAndHibernateCreateDrop() throws Exception {
        Map<String, String> flywayColumns = readColumnTypes("kraft_flyway");
        Map<String, String> hbmColumns = readColumnTypes("kraft_hbm");

        Set<String> commonTables = new HashSet<>();
        for (String key : flywayColumns.keySet()) {
            commonTables.add(key.split("\\.")[0]);
        }
        commonTables.removeAll(IGNORED_TABLES);

        Set<String> divergences = new TreeSet<>();
        for (Map.Entry<String, String> entry : flywayColumns.entrySet()) {
            String tableColumn = entry.getKey();
            String table = tableColumn.split("\\.")[0];
            if (!commonTables.contains(table)) {
                continue;
            }
            String flywayType = entry.getValue();
            String hbmType = hbmColumns.get(tableColumn);
            if (hbmType == null) {
                // Hibernate 스키마에 아예 없는 컬럼(엔티티에 매핑되지 않은 컬럼) — 별도 발견이지
                // 타입 발산은 아니다. 존재 여부 자체를 다르게 취급하고 싶다면 여기서 분기한다.
                divergences.add(tableColumn + ": flyway=" + flywayType + " hbm=(없음)");
            } else if (!flywayType.equals(hbmType)) {
                divergences.add(tableColumn + ": flyway=" + flywayType + " hbm=" + hbmType);
            }
        }

        assertThat(divergences)
                .as("Flyway(프로덕션) 스키마와 Hibernate create-drop(기본 테스트 스위트) 스키마의 "
                        + "컬럼 타입이 갈라졌다 — DB-MAP-01처럼 기본 스위트가 절대 못 잡는 버그의 "
                        + "원인이 된다. 의도된 발산이면 KNOWN_DIVERGENCES에 이유와 함께 등록하라")
                .isEqualTo(KNOWN_DIVERGENCES);
    }

    private static Set<Class<?>> scanEntityClasses() throws ClassNotFoundException {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        Set<Class<?>> entityClasses = new HashSet<>();
        for (var candidate : provider.findCandidateComponents("com.kraft")) {
            entityClasses.add(Class.forName(candidate.getBeanClassName()));
        }
        return entityClasses;
    }

    private static Map<String, String> readColumnTypes(String database) throws Exception {
        Map<String, String> columns = new HashMap<>();
        String jdbcUrl = mariadb.getJdbcUrl().replace("/" + mariadb.getDatabaseName(), "/" + database);
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl, mariadb.getUsername(), mariadb.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
                     FROM information_schema.columns
                     WHERE table_schema = DATABASE()
                     """)) {
            while (rs.next()) {
                String key = rs.getString("TABLE_NAME").toLowerCase()
                        + "." + rs.getString("COLUMN_NAME").toLowerCase();
                columns.put(key, rs.getString("DATA_TYPE").toLowerCase());
            }
        }
        return columns;
    }
}
