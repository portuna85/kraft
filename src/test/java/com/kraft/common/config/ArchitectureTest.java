package com.kraft.common.config;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.kraft.maintenance.LogRetentionScheduler;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * B-03: 이미 지켜지고 있는 의존 방향만 잠그는 경량 아키텍처 가드. 목적은 회귀
 * 방지이지 헥사고날 재설계 강제가 아니다 — 새 규칙을 추가할 때도 "지금 이미
 * 참인가"를 먼저 확인하고, 예외가 필요하면 여기 목록에 이유와 함께 명시한다.
 */
@DisplayName("아키텍처 규칙 회귀 가드")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.kraft");
    }

    @Test
    @DisplayName("common.lotto는 순수 도메인 로직이라 영속성·웹 계층에 의존하지 않는다")
    void commonLottoIsFrameworkFree() {
        // 실행해보니 LottoNumberCodec이 @Component(DI 편의)와 HttpStatus(도메인 검증
        // 실패를 ApiException(HttpStatus, ...)로 던지는 이 코드베이스의 표준 오류
        // 모델)를 쓰고 있었다 — org.springframework.. 전체를 막으면 이것까지 걸린다.
        // 이 둘은 영속성·웹 요청 계층에 대한 결합이 아니라 경량 관례라 허용한다.
        // 실제로 막아야 할 것은 JPA/영속성과 서블릿·컨트롤러 계층이다.
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.kraft.common.lotto..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..",
                        "org.springframework.web..",
                        "org.springframework.data..",
                        "com.kraft.common.web..")
                .because("common.lotto는 영속성·서블릿/웹 계층을 몰라야 하는 순수 도메인 규칙(등수, 검증, 코덱)만 담는다");

        rule.check(classes);
    }

    @Test
    @DisplayName("common.error·common.config·common.web은 특정 기능 패키지의 구현에 의존하지 않는다")
    void crossCuttingCommonDoesNotDependOnFeaturePackages() {
        // KB-17: common.web은 예전에 이 규칙 범위 밖이었다 — 그래서 ETagVersionProvider가
        // winningnumber를 직접 참조해 common→feature 순환의 한 변을 이뤄도 걸리지 않았다.
        // 그 클래스를 winningnumber로 옮기고(EtagVersionSource 인터페이스로 역전) 나서
        // common.web도 이 규칙에 포함시켜 같은 회귀를 재발 방지한다.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.kraft.common.error..", "com.kraft.common.config..",
                        "com.kraft.common.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.kraft.admin..",
                        "com.kraft.community..",
                        "com.kraft.recommend..",
                        "com.kraft.saved..",
                        "com.kraft.statistics..",
                        "com.kraft.winningnumber..",
                        "com.kraft.operationlog..",
                        "com.kraft.ops..")
                .because("오류 계약·전역 설정·공통 웹 필터는 모든 기능이 공유하는 기반이다 — 특정 기능을 알면 그 기능이 없어도 컴파일되던 코드가 깨진다");

        rule.check(classes);
    }

    @Test
    @DisplayName("컨트롤러는 리포지토리에 직접 의존하지 않고 서비스 계층을 거친다")
    void controllersDoNotDependOnRepositoriesDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.kraft..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat(
                        DescribedPredicate.describe(
                                "com.kraft 리포지토리",
                                javaClass -> javaClass.getPackageName().startsWith("com.kraft")
                                        && javaClass.getSimpleName().endsWith("Repository")))
                .because("컨트롤러가 리포지토리에 바로 접근하면 서비스 계층의 트랜잭션·검증·이벤트 발행을 건너뛴다");

        rule.check(classes);
    }

    @Test
    @DisplayName("기능 패키지는 admin(관리자 콘솔)의 구현에 의존하지 않는다 — 문서화된 예외 1건 제외")
    void featurePackagesDoNotDependOnAdmin() {
        // 유일한 허용 예외: LogRetentionScheduler(com.kraft.maintenance)는 운영 로그·관리자
        // 감사 로그를 하나의 스케줄 작업에서 함께 정리한다는 의도적 설계라 admin의
        // 리포지토리에 직접 접근한다 — 결합 자체는 없앨 수 없어 별도 leaf 패키지(KB-17)로
        // 옮겼을 뿐, 여전히 예외가 필요하다.
        DescribedPredicate<JavaClass> exceptLogRetentionScheduler = DescribedPredicate.not(
                JavaClass.Predicates.equivalentTo(LogRetentionScheduler.class));

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.kraft..")
                .and().resideOutsideOfPackage("com.kraft.admin..")
                .and(exceptLogRetentionScheduler)
                .should().dependOnClassesThat().resideInAPackage("com.kraft.admin..")
                .because("admin은 격리된 관리자 콘솔이다 — 다른 기능이 그 내부 구현(엔티티·리포지토리·필터)에 의존하면 admin을 건드릴 때 예상 밖의 기능이 함께 깨진다");

        rule.check(classes);
    }

    @Test
    @DisplayName("KB-17: 최상위 패키지 사이에 순환 의존이 없다")
    void topLevelPackagesAreFreeOfCycles() {
        // 과거 common(web.ETagVersionProvider)→winningnumber→operationlog→admin→winningnumber로
        // 닫히는 순환이 있었다(도구가 아니라 수동 grep으로만 발견됨). EtagVersionSource
        // 인터페이스 역전, WinningNumberCollectionLoggedEvent 이벤트 역전, LogRetentionScheduler를
        // maintenance로 재배치해 세 변을 끊었다 — 이 규칙은 그 회귀를 도구로 잡는다.
        // 임시 예외가 필요해지면 만료일과 사유를 반드시 주석에 남긴다.
        ArchRule rule = slices()
                .matching("com.kraft.(*)..")
                .should().beFreeOfCycles();

        rule.check(classes);
    }
}
