import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OPS-IMG-01(docs/improvement.md): Docker HEALTHCHECK가 curl을 쓰려고 런타임에
 * apt-get install curl ca-certificates를 실행했다 — 같은 base digest라도 Debian
 * 저장소 상태에 따라 패키지 구성이 달라질 수 있고(digest 고정의 재현성을 일부
 * 무효화), curl+의존 라이브러리만큼 이미지 크기·CVE 표면이 늘었다. JRE에 이미
 * 있는 java.net.http.HttpClient로 대체해 런타임 apt-get 자체를 없앤다.
 *
 * eclipse-temurin:*-jre 이미지는 jdk.compiler 모듈이 빠져 있어 `java
 * HealthCheck.java`(단일 파일 소스 실행, JEP 330)로 즉석 컴파일할 수 없다 — 그래서
 * 이 파일은 JDK가 있는 빌드 스테이지(source-build)에서 미리 .class로 컴파일한 뒤
 * 런타임 이미지에는 바이트코드만 복사한다(Dockerfile 참고).
 */
public final class HealthCheck {
    private HealthCheck() {
    }

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8080/actuator/health/readiness"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.err.println("HealthCheck: request failed: " + e);
            System.exit(1);
            return;
        }

        boolean healthy = response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"");
        if (!healthy) {
            System.err.println("HealthCheck: unhealthy (status=" + response.statusCode()
                    + ", body=" + response.body() + ")");
            System.exit(1);
        }
    }
}
