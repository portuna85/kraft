import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

configure<CheckstyleExtension> {
    toolVersion = "10.23.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = (project.findProperty("strictStatic") != "true")
}

tasks.withType<Checkstyle> {
    reports {
        xml.required = false
        html.required = true
    }
}
