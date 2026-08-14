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
            // 레이트리밋 카운터 상태는 PublicRateLimitFilter/CommunityWriteRateLimitFilter에서
            // RateLimitCounter로 옮겨졌다(2026-07-31) — 두 필터는 더 이상 직접 상태를 들지 않고,
            // kraft.security.rate-limit-backend=in-memory(기본값)일 때만 이 클래스가 인스턴스별
            // Caffeine 캐시를 갖는다. redis 백엔드(RedisRateLimitCounter)는 인스턴스 간 공유되므로
            // 이 목록에 없다.
            "com.kraft.common.web.InMemoryRateLimitCounter",
            // H-04(2026-08-12): Redis 장애 경고 로그를 스로틀하기 위한 AtomicLong 타임스탬프뿐이다.
            // 레이트리밋 카운트 자체는 여전히 전부 Redis에 있어 인스턴스 간 공유되므로, 이 필드가
            // 인스턴스별로 남아도(각 인스턴스가 자기 로그만 독립적으로 스로틀해도) 정확성에 영향이 없다.
            "com.kraft.common.web.RedisRateLimitCounter",
            "com.kraft.admin.AdminLoginAttemptService",
            "com.kraft.winningnumber.RoundEtagProvider",
            "com.kraft.recommend.LottoRecommendationService",
            // TD-008 1단계(2026-08-14): LottoRecommendationService가 들고 있던 volatile
            // historySnapshot을 RecommendationHistorySnapshotManager로 옮겼다 — 새로 추가된
            // 상태가 아니라 기존에 이미 검토됐던 인스턴스별 스냅샷이 그대로 이동한 것이다(DB가
            // 단일 진실 공급원이라 인스턴스마다 독립적으로 리빌드해도 안전, 클래스 상단 NOTE 참고).
            "com.kraft.recommend.RecommendationHistorySnapshotManager",
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
