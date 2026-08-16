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
