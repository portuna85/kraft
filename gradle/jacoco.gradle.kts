import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

// apply(from = ...)로 포함된 스크립트에는 타입-세이프 accessor(jacoco {}, tasks.jacocoTestReport
// 등)가 생성되지 않는다 — 그건 각 스크립트 자신의 plugins{} 블록을 기준으로만 만들어진다.
// 그래서 여기서는 configure<T>()/tasks.named<T>()로 명시적으로 타입을 지정한다(TD-023).
configure<JacocoPluginExtension> {
    toolVersion = "0.8.13"
}

// Patterns excluded from coverage measurement (shared by report + verification).
// Excludes: Spring config/properties, DTO/record boilerplate, JPA entities
// (no logic beyond getters), enums, event records, and admin Thymeleaf layer
// (controller/handlers/filter — covered by smoke tests, not unit tests).
val jacocoExcludes = listOf(
    "**/Application.class",
    "**/config/**",
    "**/*Properties.class",
    "**/*Response.class",
    "**/*Request.class",
    "**/*Dto.class",
    "**/db/migration/**",
    // JPA entities — getters/constructors only, no business logic
    "**/winningnumber/WinningNumber.class",
    "**/saved/SavedNumber.class",
    "**/admin/AdminUser.class",
    "**/admin/AdminAuditLog.class",
    "**/statistics/FrequencySummary.class",
    "**/statistics/PatternStatsSummary.class",
    "**/statistics/CompanionPairSummary.class",
    "**/operationlog/WinningNumberOperationLog.class",
    // Enums
    "**/WinningNumberOperationType.class",
    "**/WinningNumberOperationStatus.class",
    // Event records
    "**/*Event.class",
    // Admin Thymeleaf layer — tested by smoke tests, not unit tests
    "**/admin/AdminController.class",
    "**/admin/AdminLoginHandler.class",
    "**/admin/AdminLockoutFilter.class"
)

fun jacocoClassDirs() = fileTree(layout.buildDirectory.dir("classes/java/main")) {
    exclude(jacocoExcludes)
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named<Test>("test"), tasks.named("isolatedLoggingTest"))
    executionData(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(jacocoClassDirs())
}

// I-05: 기본값을 엄격 모드로 뒤집었다 — 예전에는 -PstrictCoverage=true를 명시해야만
// jacocoTestCoverageVerification가 check에 연결됐고, 평범한 `./gradlew check`는 이
// task 자체를 아예 안 만들어 커버리지 하락이 실패는커녕 눈에 보이지도 않았다. 빠른
// 로컬 점검이 필요할 때만 의도가 드러나는 이름으로 opt-out한다.
val skipCoverageGate = project.findProperty("skipCoverageGate") == "true"
if (!skipCoverageGate) {
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named<JacocoReport>("jacocoTestReport"))
        executionData(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
        classDirectories.setFrom(jacocoClassDirs())
        violationRules {
            // 전체 프로젝트 임계값
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.82".toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = "0.65".toBigDecimal()
                }
                limit {
                    counter = "METHOD"
                    value = "COVEREDRATIO"
                    minimum = "0.88".toBigDecimal()
                }
                limit {
                    counter = "CLASS"
                    value = "COVEREDRATIO"
                    minimum = "0.97".toBigDecimal()
                }
            }
            // 핵심 도메인 패키지별 라인 커버리지 임계값
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/saved")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.85".toBigDecimal() }
            }
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/recommend")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.85".toBigDecimal() }
            }
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/winningnumber")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.80".toBigDecimal() }
            }
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/statistics")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.80".toBigDecimal() }
            }
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/ops")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.80".toBigDecimal() }
            }
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/common/web")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.75".toBigDecimal() }
            }
        }
    }
    tasks.named("check") { dependsOn(tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification")) }
}
