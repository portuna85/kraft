package com.kraft.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * B-04: 인스턴스별로만 유지되고 다중 인스턴스 간 공유되지 않는 상태(Caffeine 캐시,
 * Atomic 카운터, volatile 스냅숏)를 가진 클래스를 인벤토리로 고정한다. 새 클래스가
 * 이런 필드를 추가하면 이 테스트가 실패해, 그 상태가 인스턴스별로 안전한지(공유 저장소가
 * 필요 없는지) 수평 확장 관점에서 의식적으로 검토하고 목록에 추가하도록 강제한다 —
 * 목록에 있다는 것 자체가 "이미 검토해 인스턴스별 상태여도 안전하다고 결론 낸 것"이라는
 * 뜻이다(각 클래스 상단의 NOTE 주석에 근거가 있다).
 */
@DisplayName("인스턴스별 인메모리 상태 보유 클래스 인벤토리")
class InMemoryStateInventoryTest {

    private static final Set<String> KNOWN_HOLDERS = Set.of(
            "com.kraft.common.web.PublicRateLimitFilter",
            "com.kraft.community.auth.CommunityWriteRateLimitFilter",
            "com.kraft.admin.AdminLoginAttemptService",
            "com.kraft.winningnumber.RoundEtagProvider",
            "com.kraft.recommend.LottoRecommendationService",
            "com.kraft.winningnumber.LottoFreshnessMetrics");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.kraft");
    }

    @Test
    @DisplayName("새 인스턴스별 상태 보유 클래스는 검토 없이 조용히 추가될 수 없다")
    void inMemoryStateHoldersMatchKnownInventory() {
        Set<String> actualHolders = classes.stream()
                .filter(InMemoryStateInventoryTest::holdsInstanceLocalState)
                .map(JavaClass::getName)
                .collect(Collectors.toSet());

        assertThat(actualHolders)
                .as("Caffeine 캐시·Atomic 카운터·volatile 필드를 가진 클래스는 KNOWN_HOLDERS에 "
                        + "명시적으로 등록돼야 한다 — 새로 걸린 클래스가 있다면 그 상태가 수평 확장 시 "
                        + "인스턴스별로 남아도 안전한지 검토한 뒤(§ B-04) 클래스 상단에 NOTE 주석을 "
                        + "남기고 이 목록에 추가하라")
                .isEqualTo(KNOWN_HOLDERS);
    }

    private static boolean holdsInstanceLocalState(JavaClass javaClass) {
        return javaClass.getFields().stream().anyMatch(InMemoryStateInventoryTest::isStatefulField);
    }

    private static boolean isStatefulField(JavaField field) {
        if (field.getModifiers().contains(JavaModifier.VOLATILE)) {
            return true;
        }
        JavaClass rawType = field.getRawType();
        return rawType.isAssignableTo(Cache.class)
                || rawType.isEquivalentTo(AtomicInteger.class)
                || rawType.isEquivalentTo(AtomicLong.class)
                || rawType.isEquivalentTo(AtomicReference.class);
    }
}
