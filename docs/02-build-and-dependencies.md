# 02. 빌드와 의존성

## 2.1 build.gradle.kts 전문 분석

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.kraft"
version = "0.0.1-SNAPSHOT"
description = "kraft"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

- **`group = "com.kraft"`** — ✅ **2026-09-09 해결(P2-8)**. 분석 당시에는 `group = "com"`이었다.
  관례상 `com.kraft` 또는 조직 도메인을 사용해야 하는데, 당시 값은 패키지(`com.kraft`)와도
  어긋나 아티팩트 좌표가 `com:kraft:0.0.1-SNAPSHOT`이 되는 문제가 있었다. `./gradlew properties`로
  실제 반영값이 `group: com.kraft`임을 확인했다. 순수 메타데이터(Maven 좌표) 변경이라
  `group`은 실행 산출물의 JAR 파일명(`kraft-0.0.1-SNAPSHOT.jar`, `settings.gradle.kts`의
  `rootProject.name`에 의해 결정됨)이나 패키지 구조(이미 `com.kraft`)에는 영향을 주지 않는다 —
  이 프로젝트를 다른 프로젝트가 Maven/Gradle 의존성으로 참조할 때 쓰는 좌표만 바뀐다.
  `./gradlew clean test`로 회귀 없음을 재확인했다(44개 테스트 전부 통과).
- **Java 25 toolchain**: Gradle이 JDK 25를 자동 프로비저닝하거나 로컬에서 탐색합니다. Spring Boot 4.x는 최소 Java 17, Java 25(LTS)를 지원합니다.
- **Gradle 9.7.1**: `gradle/wrapper/gradle-wrapper.properties` 기준. Spring Boot 4 플러그인이 요구하는 Gradle 8.x 이상 조건을 충족합니다.

## 2.2 의존성 목록과 역할

### 2.2.1 Spring Boot 4의 모듈형 스타터

Spring Boot 4에서는 기존의 거대 스타터가 세분화되었습니다. 이 프로젝트는 신규 명칭을 사용합니다.

| 선언된 의존성 | 역할 | Boot 3 대응 |
| --- | --- | --- |
| `spring-boot-starter-webmvc` | Spring MVC + 내장 Tomcat + Jackson | `spring-boot-starter-web` |
| `spring-boot-h2console` | H2 웹 콘솔 자동 구성 | `spring-boot-devtools` 내 포함 기능 |
| `spring-boot-starter-session-jdbc` | Spring Session의 JDBC 저장소 | `spring-session-jdbc` |
| `spring-boot-starter-security` | 인증/인가 필터 체인 | 동일 |
| `spring-boot-starter-thymeleaf` | Thymeleaf 3 템플릿 엔진 | 동일 |
| `spring-boot-starter-validation` | Jakarta Bean Validation (Hibernate Validator) | 동일 |
| `spring-boot-starter-data-jpa` | Spring Data JPA + Hibernate + HikariCP | 동일 |
| `spring-boot-starter-mail` | `JavaMailSender` 기반 이메일 발송 | `spring-boot-starter-mail`(Boot 3와 동일) |

> **2026-09-10 추가**: `spring-boot-starter-mail`은 이메일 인증 기능(P3-4) 구현 시 도입된,
> 이 프로젝트에서 유일한 신규 의존성이다. 상세는 [07장 7.8절](07-configuration.md#78-이메일-발송-설정-springmail--구현-완료-2026-09-10-p3-4),
> [08장 8.15절](08-issues-and-todo.md#815-추가-구현-p3-4-이메일-인증-플로우-2026-09-10) 참고.

### 2.2.2 런타임 / 서드파티

| 의존성 | 스코프 | 비고 |
| --- | --- | --- |
| `org.thymeleaf.extras:thymeleaf-extras-springsecurity6` | implementation | 템플릿에서 `sec:authorize`, `sec:authentication` 사용 가능 |
| `org.projectlombok:lombok` | implementation | **스코프 오류 — 2.3절 참고** |
| `com.h2database:h2` | runtimeOnly | 개발/테스트용 인메모리 DB |
| `org.mariadb.jdbc:mariadb-java-client` | runtimeOnly | 운영 DB 드라이버. **현재 `application.yml`에 관련 설정 없음** |

### 2.2.3 테스트

> **2026-09-09 업데이트**: `spring-boot-starter-data-jpa-test`가 실제로 추가되었고,
> 아래 스타터를 활용한 계층별 테스트 44개가 작성되었다(P2-15). 상세는
> [09장 11절](09-implementation-summary.md#11-추가-구현-계층별-테스트-코드-2026-09-09-p2-15) 참고.

| 의존성 | 제공 기능 |
| --- | --- |
| `spring-boot-starter-webmvc-test` | `@WebMvcTest`, `MockMvc`, JUnit 5, AssertJ |
| `spring-boot-starter-security-test` | `@WithMockUser`, `SecurityMockMvcRequestPostProcessors` |
| `spring-boot-starter-thymeleaf-test` | 템플릿 렌더링 검증 |
| `spring-boot-starter-validation-test` | 검증 애노테이션 테스트 지원 |
| `spring-boot-starter-data-jpa-test` (2026-09-09 추가) | `@DataJpaTest`, `TestEntityManager` |
| `junit-platform-launcher` (testRuntimeOnly) | Gradle의 JUnit 5 실행 |

`spring-boot-starter-data-jpa`에 대응하는 `spring-boot-starter-data-jpa-test`는 최초 분석 시점에는
선언되어 있지 않았다. Boot 4.1.1에서 실제로 해석되는 아티팩트인지 `./gradlew dependencies`로
먼저 확인한 뒤(추측으로 존재하지 않는 좌표를 적어 빌드를 깨뜨리지 않기 위함) 추가했다.

## 2.3 [해결됨] Lombok 애노테이션 프로세서 미등록

> **2026-09-09 업데이트**: 아래 2.3절은 문제 발견 당시 기록이다. `build.gradle.kts`는 2.5절의
> 개선안대로 이미 수정되었고 `./gradlew compileJava`가 정상 통과함을 확인했다. 상세는
> [09. 구현 요약](09-implementation-summary.md) 참고.

### 현상

```kotlin
implementation("org.projectlombok:lombok")   // 현재 상태
```

Gradle에서 Lombok은 **컴파일 클래스패스에 존재하는 것만으로는 동작하지 않습니다.**
`annotationProcessor` 구성에 등록되어야 `javac`가 애노테이션 프로세서로 인식합니다.

실제 `./gradlew compileJava` 결과, Lombok이 생성해야 할 코드가 만들어지지 않아 다음 오류가 발생합니다.

```
Role.java:10: error: constructor Role in enum Role cannot be applied to given types;
    ADMIN("ROLE_ADMIN", "관리자"),
         ^
  required: no arguments        ← @RequiredArgsConstructor 미생성
User.java:48: error: cannot find symbol
        return this.role.getKey();
                        ^
  symbol: method getKey()       ← @Getter 미생성
```

`@RequiredArgsConstructor`가 붙은 `PostService`, `UserService`, `PostsApiController`의
생성자 주입도 동일하게 실패하므로, **런타임에도 빈 생성이 불가능**합니다.

### 해결

```kotlin
compileOnly("org.projectlombok:lombok")
annotationProcessor("org.projectlombok:lombok")

testCompileOnly("org.projectlombok:lombok")
testAnnotationProcessor("org.projectlombok:lombok")
```

- `compileOnly`: Lombok 애노테이션은 컴파일 시점에만 필요하고 런타임 산출물에 포함될 필요가 없습니다.
  현재의 `implementation`은 불필요하게 Lombok JAR을 실행 아티팩트에 포함시킵니다.
- `testCompileOnly` / `testAnnotationProcessor`: 테스트 코드에서도 Lombok을 쓸 경우 필요합니다.

## 2.4 Gradle 태스크

| 명령 | 설명 |
| --- | --- |
| `./gradlew build` | 컴파일 + 테스트 + `build/libs/kraft-0.0.1-SNAPSHOT.jar` 생성 |
| `./gradlew bootRun` | 애플리케이션 실행 (기본 8080) |
| `./gradlew test` | JUnit 5 테스트 (`useJUnitPlatform()` 설정됨) |
| `./gradlew bootBuildImage` | OCI 이미지 빌드 (Boot 플러그인 제공) |
| `./gradlew clean` | `build/` 정리 |

Windows PowerShell 환경에서는 `.\gradlew.bat` 또는 `.\gradlew`를 사용합니다.

## 2.5 개선 제안 (현행 라이브러리 범위 내) — 적용 완료

```kotlin
dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    // ... 기존 의존성 유지
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}
```

위 스코프 변경이 `build.gradle.kts`에 실제 반영되었다. `group = "com.kraft"` 변경은 패키지 구조에는
영향이 없고 Maven 좌표 정리에만 관련된 항목이라 이번 P0/P1 범위에서는 적용하지 않았다(P2 후보).

`tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }`는
Spring Boot 플러그인이 이미 적용하므로 별도 선언이 필요 없습니다.
