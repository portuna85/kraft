package com.kraft.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * QA-BE-01(docs/improvement.md): {@code nativeQuery = true}인 리포지토리 메서드는 MySQL
 * 방언 SQL이라 H2 기본 테스트 스위트에서 **구조적으로 실행 불가능하다** — 실제로 실행되는지
 * 확인하는 유일한 방법은 Testcontainers MariaDB 테스트뿐이다. 이 가드는 새 native 쿼리가
 * 추가됐는데 대응하는 Testcontainers 테스트 없이 조용히 병합되는 것을 막는다 — 새 메서드가
 * 걸리면 이 테스트가 실패해, KNOWN_NATIVE_QUERIES에 커버리지 테스트 클래스를 명시해야만
 * 통과한다(InMemoryStateInventoryTest와 같은 인벤토리 패턴).
 */
@DisplayName("네이티브 쿼리 메서드의 Testcontainers 커버리지 인벤토리")
class NativeQueryCoverageTest {

    /**
     * 값은 "실제로 이 SQL을 실 MariaDB로 실행하는" 테스트 클래스 이름이다(문서용 — 리플렉션
     * 검증 대상 아님). 항목을 추가하려면 대응 Testcontainers 테스트를 먼저 만들거나, 왜
     * 기존 테스트가 이미 커버하는지(예: 서비스 메서드를 통한 간접 호출) 근거를 남겨야 한다.
     */
    private static final Set<String> KNOWN_NATIVE_QUERIES = Set.of(
            // CommunityPostRepositoryNativeQueryTest가 findWeeklyPopular를 직접 호출해
            // POW/TIMESTAMPDIFF/UTC_TIMESTAMP(6) 정렬 공식과 7일 컷오프를 실제 MariaDB로 검증한다.
            "com.kraft.community.post.CommunityPostRepository.findWeeklyPopular"
                    + "(java.lang.String, java.lang.String, "
                    + "java.time.OffsetDateTime, org.springframework.data.domain.Pageable)",
            // findWeeklyPopular와 SQL은 같지만 별도 메서드(countQuery 없는 List 반환)라
            // 독립적으로 검증한다 — CommunityPostRepositoryNativeQueryTest에서 함께 커버.
            "com.kraft.community.post.CommunityPostRepository.findWeeklyPopularPublished"
                    + "(java.lang.String, java.lang.String, "
                    + "java.time.OffsetDateTime, org.springframework.data.domain.Pageable)",
            // CommunityBlockConcurrencyTest가 CommunityBlockService.block()을 실 MariaDB로
            // 동시 호출해 upsertBlock의 ON DUPLICATE KEY UPDATE 원자성을 간접 검증한다.
            "com.kraft.community.block.CommunityUserBlockRepository.upsertBlock"
                    + "(java.lang.Long, java.lang.Long, java.time.OffsetDateTime)",
            // SavedNumberClientLockRepositoryTest가 직접 호출한다.
            "com.kraft.saved.SavedNumberClientLockRepository.ensureLockRowExists"
                    + "(java.lang.String, java.time.OffsetDateTime)",
            // SavedNumberClientLockRepositoryTest가 직접 호출한다.
            "com.kraft.saved.SavedNumberClientLockRepository.deleteOrphansOlderThan"
                    + "(java.time.OffsetDateTime)");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.kraft");
    }

    @Test
    @DisplayName("새 native 쿼리 메서드는 커버리지 검토 없이 조용히 추가될 수 없다")
    void nativeQueryMethodsMatchKnownInventory() {
        Set<String> actual = classes.stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(NativeQueryCoverageTest::isNativeQuery)
                .map(NativeQueryCoverageTest::signatureOf)
                .collect(Collectors.toSet());

        assertThat(actual)
                .as("nativeQuery=true 메서드는 실 MariaDB로 검증하는 Testcontainers 테스트가 "
                        + "있어야 한다(H2에서는 구조적으로 실행 불가능하다 — MySQL 방언 SQL). "
                        + "새 메서드가 걸렸다면 대응 테스트를 추가한 뒤 KNOWN_NATIVE_QUERIES에 "
                        + "그 테스트 클래스를 주석으로 남기고 등록하라")
                .isEqualTo(KNOWN_NATIVE_QUERIES);
    }

    private static boolean isNativeQuery(JavaMethod method) {
        return method.isAnnotatedWith(Query.class)
                && method.getAnnotationOfType(Query.class).nativeQuery();
    }

    private static String signatureOf(JavaMethod method) {
        String params = method.getParameterTypes().stream()
                .map(type -> type.toErasure().getName())
                .collect(Collectors.joining(", "));
        return method.getOwner().getName() + "." + method.getName() + "(" + params + ")";
    }
}
