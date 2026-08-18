import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

// I-05: 기본값을 엄격 모드로 뒤집었다 — 예전에는 -PstrictStatic=true를 명시해야만
// Checkstyle 위반이 빌드를 실패시켰고, 평범한 `./gradlew check`(로컬/IDE/외부 CI)는
// 위반을 조용히 통과시켰다. 빠른 로컬 점검이 필요할 때만 의도가 드러나는 이름으로
// opt-out한다.
configure<CheckstyleExtension> {
    toolVersion = "10.23.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = (project.findProperty("skipStaticGate") == "true")
}

tasks.withType<Checkstyle> {
    reports {
        xml.required = false
        html.required = true
    }
}

// 2026-08-18 CVE 대응: checkstyle 구성의 net.sf.saxon:Saxon-HE(→xmlresolver)가 자체
// 모듈 메타데이터에서 httpclient5/httpcore5/httpcore5-h2를 5.1.3으로 `strictly` 고정한다.
// CVE-2026-54399(httpcore5), CVE-2026-54428(httpcore5-h2) 모두 5.4.3에서 수정됐는데,
// strictly 고정은 일반 constraints로는 못 넘어서고 force로만 넘어설 수 있다. 정적 분석
// 도구 전용 classpath라 애플리케이션 런타임에는 영향이 없다.
// httpclient5를 5.1.3 → 5.4.3으로 올리면 그 자신의 commons-codec 요구가 느슨해져
// 락파일의 commons-codec이 1.15 → 1.11로 같이 내려간다 — 프로젝트 다른 곳(testCompile/
// testRuntime)이 이미 쓰는 최신 버전으로 맞춰 낮은 버전이 섞이지 않게 한다.
configurations.named("checkstyle") {
    resolutionStrategy {
        force(
            "org.apache.httpcomponents.client5:httpclient5:5.4.3",
            "org.apache.httpcomponents.core5:httpcore5:5.4.3",
            "org.apache.httpcomponents.core5:httpcore5-h2:5.4.3",
            "commons-codec:commons-codec:1.21.0",
        )
    }
}
