# 07. 설정과 실행

> **2026-09-09 업데이트**: 7.1절은 구현 전 시점의 단일 `application.yml`이다. 이후 7.7절처럼
> `local`/`prod` 프로파일로 분리되었고, `bootRun`으로 정상 기동해 `h2-console` 접속, 세션 테이블
> 생성, `email` 유니크 제약 반영까지 확인했다. `SecurityConfig`도 7.3절 그대로 구현되어 있다.
> 프로파일 분리(7.4절)는 최초에는 P2로 제외했으나 이후 추가 구현·검증했다.

## 7.1 구현 전 application.yml (분석 당시)

```yaml
spring:
  application:
    name: kraft

  jpa:
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect

  session:
    store-type: jdbc
```

전체 설정이 12줄이며, 의존성에 선언된 기능 대비 **누락된 항목이 많았습니다.** 실제 반영된 최종
설정은 [7.7절](#77-최종-반영된-설정-2026-09-09-프로파일-3파일-구성)을 참고하세요.

## 7.2 항목별 분석 (문제 진단 — 이후 해결 여부는 각 소절에 표시)

### 7.2.1 `spring.jpa.show-sql: true`

Hibernate가 생성한 SQL을 표준 출력으로 찍습니다. 개발 단계에서는 유용합니다.

**개선**: `show-sql`은 로깅 프레임워크를 거치지 않고 `System.out`으로 직접 출력합니다.
로거를 통해 포맷과 레벨을 제어하는 방식이 더 낫습니다.

```yaml
spring:
  jpa:
    properties:
      hibernate:
        format_sql: true
logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace   # 바인딩 파라미터 확인
```

운영 프로파일에서는 반드시 꺼야 합니다.

### 7.2.2 `hibernate.dialect: H2Dialect` — 하드코딩 문제 — ✅ 해결(제거)

의존성에 **MariaDB 드라이버가 함께 선언**되어 있습니다.

```kotlin
runtimeOnly("com.h2database:h2")
runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
```

MariaDB로 접속하는 순간 방언이 H2로 고정되어 있어 잘못된 SQL이 생성됩니다.

**Hibernate 6 이상은 JDBC 메타데이터로 방언을 자동 판별**하므로,
특별한 이유가 없다면 `dialect` 설정 자체를 **제거**하는 것이 정답입니다.

실제로 `dialect` 줄을 삭제했다. `bootRun` 로그에서 `Database dialect: H2Dialect`로 자동 판별되어
동일한 결과를 얻으면서도 하드코딩이 사라졌음을 확인했다.

### 7.2.3 `spring.session.store-type: jdbc` — 스키마 초기화 누락 — ✅ 해결

Spring Session JDBC는 `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` 두 테이블을 요구합니다.
`spring-boot-starter-session-jdbc`는 DB별 스키마 SQL을 JAR 안에 포함하고 있으며,
**초기화 모드를 명시해야** 자동 생성됩니다.

```yaml
spring:
  session:
    jdbc:
      initialize-schema: always     # 운영에서는 never + 수동 DDL 적용
    timeout: 30m
```

`initialize-schema`의 기본값은 `embedded`(내장 DB에서만 생성)이므로
H2 인메모리에서는 동작하지만 **MariaDB로 전환하는 즉시 테이블 부재로 실패**합니다.

`initialize-schema: always`를 실제로 적용했다(`timeout: 30m`은 이번 범위에서는 추가하지 않음,
필요 시 P2에서 검토). `bootRun` 로그에서 세션 관련 오류 없이 정상 기동함을 확인했다.

### 7.2.4 [누락] DataSource 설정 — ✅ 해결

`spring.datasource` 블록이 전혀 없습니다.
H2 드라이버가 클래스패스에 있으므로 Spring Boot가 **임의 이름의 인메모리 DB**를 자동 구성합니다.
동작은 하지만 다음 문제가 있습니다.

- 재기동 시 데이터가 전부 사라짐
- H2 콘솔에서 접속할 JDBC URL을 매번 로그에서 찾아야 함

의존성에 `spring-boot-h2console`이 있으므로 **고정 URL 지정**을 권장합니다.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:kraft;MODE=MariaDB;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
```

`MODE=MariaDB`를 주면 운영 DB와 SQL 호환성을 미리 검증할 수 있습니다.

실제 반영값은 `jdbc:h2:mem:kraft;MODE=MariaDB`이다(`DB_CLOSE_DELAY=-1`, `h2.console.path`는
기본값(`/h2-console`)과 동일해 생략했다). `h2-console` 접속과 `USERS`/`POSTS` 테이블 생성을
curl로 확인했다.

### 7.2.5 [누락] `spring.jpa.hibernate.ddl-auto` — ✅ 해결

명시가 없어 Boot의 기본값이 적용됩니다(내장 DB는 `create-drop`, 그 외는 `none`).
**MariaDB로 전환하면 테이블이 하나도 만들어지지 않아** 즉시 실패합니다.
프로파일별로 명시해야 합니다.

| 환경 | 권장 값 |
| --- | --- |
| 로컬(H2) | `create-drop` 또는 `update` |
| 운영(MariaDB) | `validate` (스키마는 마이그레이션 도구나 수동 DDL로 관리) |

로컬 기준으로 `create-drop`을 적용했다. 운영(MariaDB) 대응 `validate` 프로파일 분리는 하지
않았다(P2, 7.4절 그대로 유효).

### 7.2.6 [누락] `open-in-view` — ✅ 해결(2026-09-09 추가 구현)

Spring Boot는 `spring.jpa.open-in-view`를 기본 `true`로 두고, 기동 시 경고 로그를 남깁니다.
영속성 컨텍스트를 뷰 렌더링까지 열어 두어 DB 커넥션 점유 시간이 길어집니다.

[04장](04-architecture-and-layers.md)의 지침대로 **서비스 계층에서 DTO 변환을 끝내면**
OSIV가 필요 없으므로 끄는 것을 권장합니다.

```yaml
spring:
  jpa:
    open-in-view: false
```

공통 `application.yml`에 실제로 추가했다. `IndexController`가 `PostService`의 DTO(record)
반환값만 `Model`에 담고 엔티티를 뷰까지 전달하지 않으므로 안전했다. `bootRun` 로그에서
`spring.jpa.open-in-view is enabled by default` 경고가 사라졌음을 확인했고, `GET /`(목록
화면)이 여전히 `200`으로 정상 렌더링되어 `LazyInitializationException`이 없음을 curl로
확인했다([08장 8.12절](08-issues-and-todo.md#812-추가-구현-p2-1p2-6p2-7p2-9p2-11-나머지-정리-2026-09-09) 참고).

### 7.2.7 [누락] Thymeleaf 캐시 — ✅ 해결

개발 중 템플릿 수정을 즉시 반영하려면 캐시를 꺼야 합니다.

```yaml
spring:
  thymeleaf:
    cache: false      # 운영에서는 true
```

## 7.3 [해결됨] SecurityConfig 부재

`spring-boot-starter-security`가 클래스패스에 있고 `SecurityFilterChain` 빈이 없으면
Spring Boot는 **모든 요청에 인증을 요구하는 기본 체인**을 구성합니다. 아래는 구현 전 증상이었다.

- 기동 로그에 랜덤 생성 비밀번호 출력 (`Using generated security password: ...`)
- 사용자명은 `user`
- 게시글 목록(`/`)조차 로그인 없이는 볼 수 없음 → **"모든 사용자는 Post 조회 가능"이라는 기획과 배치됨**
- `/h2-console` 접근 불가 (CSRF + frame options로 이중 차단)
- AJAX 쓰기 요청이 CSRF 토큰 부재로 403 ([05장 5.5절](05-api-spec.md) 참고)

### 실제 구현 (`config/security/SecurityConfig.java`) — ✅ 반영 완료

```java
package com.kraft.config.security;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/css/**", "/js/**", "/images/**", "/h2-console/**").permitAll()
                        .requestMatchers("/api/v1/users").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(refererLogoutSuccessHandler())
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/h2-console/**")
                );

        return http.build();
    }

    // 로그아웃 성공 시 Referer(같은 오리진일 때만)로 돌려보낸다. 2026-09-10 추가 — 6.6절 참고.
    private LogoutSuccessHandler refererLogoutSuccessHandler() { ... }
}
```

> **2026-09-10 변경**: `loginPage("/login")`과 `refererLogoutSuccessHandler()`는 프론트엔드
> 디자인 개선(6.6절)과 그 직후 발견된 `/logout` 404 회귀 수정 과정에서 추가됐다. 둘 다
> `authorizeHttpRequests` 규칙은 건드리지 않았다 — `/login`·`/logout`(GET/POST) 모두 기존
> `anyRequest().permitAll()` catch-all이 이미 커버하고 있었기 때문이다. 상세 배경은
> [06장](06-view-and-templates.md#로그인-화면-커스텀-템플릿-추가-2026-09-10)과
> [06장 로그아웃 회귀 절](06-view-and-templates.md#회귀-발견수정-logout이-404로-깨짐-2026-09-10)
> 참고.

**당초 제안과의 차이**(구현 과정에서 내린 실제 결정):

- `hasRole(Role.USER.name())` 대신 **`authenticated()`**를 사용했다. 기획상 `GUEST`도 로그인은
  가능해야 하고(이메일 인증 전), 이번 범위에서는 역할 기반 인가까지 세분화하지 않기로 했다(P2).
- `loginPage("/login")`을 명시하지 않고 **Spring Security 기본 로그인 페이지**를 그대로 사용했다.
  `GET /login`이 `200`으로 정상 응답함을 확인했다. **2026-09-10 변경**: 프론트엔드 디자인
  개선(6.6절) 과정에서 `.loginPage("/login")`을 명시하고 `templates/user/login.html`을 새로
  만들어 다른 화면과 동일한 네비게이션 바·카드형 레이아웃을 적용했다. `anyRequest().permitAll()`
  catch-all이 이미 `/login`(GET/POST)을 커버하고 있어 `authorizeHttpRequests` 규칙은 손대지
  않았다 — 상세 배경은 [06장](06-view-and-templates.md#로그인-화면-커스텀-템플릿-추가-2026-09-10)
  참고. 로그인 실패 시 리다이렉트되는 `/login?error`,
  기본 성공 URL(`/`)은 동일하게 유지된다.
- 마지막 규칙을 `anyRequest().authenticated()`가 아니라 **`anyRequest().permitAll()`**로 뒀다.
  `/posts/save`, `/posts/update/{id}` 같은 화면 라우팅은 비로그인 사용자도 접근은 가능하게 하고,
  실제 쓰기는 `/api/v1/**`의 `authenticated()` 규칙이 막는다(화면은 보이되 등록 시도 시 로그인
  페이지로 리다이렉트). curl로 로그인 없이 `POST /api/v1/posts`를 호출하면 CSRF 토큰이 있어도
  `302`(로그인 페이지로)로 막힘을 확인했다.
- `@RequiredArgsConstructor`는 필드가 없어(주입할 의존성 없음) 제거했다.

주의할 점(원 제안에서 유효한 부분):

- **`/h2-console`의 CSRF 예외**는 로컬 개발 전용이다. 운영 프로파일이 분리되면 그때는
  `/h2-console/**` 자체를 차단해야 한다(P2, 7.4절).
- 나머지 경로의 CSRF는 끄지 않았고, 프런트에서 토큰을 헤더로 전달하도록 구현했다
  ([05장 5.5절](05-api-spec.md#55-csrf---해결됨-실제-curl로-검증-완료) 참고).
- 인증 주체 조회는 `UserDetailsServiceImpl`(`UserRepository.findByEmail` 기반)이 담당한다.

## 7.4 프로파일 분리 — ✅ 해결됨 (2026-09-09 추가 구현)

> 최초 P0+P1 구현에서는 단일 `application.yml`로 충분하다고 판단해 프로파일을 분리하지
> 않았으나, 이후 사용자 요청으로 아래와 같이 `local`/`prod` 프로파일을 실제로 분리했다.
> 파일 3개(`application.yml`, `application-local.yml`, `application-prod.yml`)로 구성되며,
> 아래 내용은 제안이 아니라 **실제 반영된 코드**다.

`application.yml` (공통 — 두 프로파일 모두에 적용):

```yaml
spring:
  application:
    name: kraft

  profiles:
    default: local

  session:
    store-type: jdbc
```

`application-local.yml` (`spring.profiles.default: local`이므로 프로파일을 명시하지 않으면
자동 적용됨 — 기존 `./gradlew bootRun`, `./gradlew test` 사용법이 그대로 유지됨):

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:kraft;MODE=MariaDB
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

  h2:
    console:
      enabled: true

  session:
    jdbc:
      initialize-schema: always

  thymeleaf:
    cache: false

logging:
  level:
    org.hibernate.SQL: debug
```

`application-prod.yml` (`--spring.profiles.active=prod`로 명시적으로 활성화해야만 적용됨):

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.mariadb.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

  h2:
    console:
      enabled: false

  session:
    jdbc:
      initialize-schema: never

  thymeleaf:
    cache: true
```

**당초 제안과 다른 점**: 공통 `application.yml`에 `jpa.properties.hibernate.format_sql: true`는
넣지 않았다(무관한 설정 변경을 함께 끼워 넣지 않기 위함). `jpa.open-in-view: false`는 이
문서 작성 시점에는 보류했으나, ✅ **2026-09-09에 별도로 추가 구현**했다(7.2.6절 참고) — 프로파일과
무관하게 공통 `application.yml`에 적용된다.

운영 자격 증명(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`)은 **파일에 하드코딩하지 않고 환경변수로
주입**한다. `h2.console.enabled: false`로 운영에서는 H2 콘솔 자체를 비활성화했다 — `SecurityConfig`가
`/h2-console/**`를 `permitAll` + CSRF 예외로 열어두고 있지만([07장 7.3절](#73-해결됨-securityconfig-부재)),
콘솔이 꺼지면 그 경로를 가리킬 대상 자체가 없어져 무해해진다(코드 분기 없이 설정만으로 해결).

### 검증(curl/로그, 실제 3가지 시나리오로 확인)

| 시나리오 | 실행 | 결과 |
| --- | --- | --- |
| 기본(무프로파일) 기동 | `./gradlew bootRun` | 로그에 `"falling back to 1 default profile: 'local'"`, 이전과 동일하게 `/`, `/h2-console`, `/api/v1/posts/list` 모두 정상(회귀 없음) |
| `prod` 프로파일, 환경변수 없음 | `./gradlew bootRun --args="--spring.profiles.active=prod"` | 로그에 `"The following 1 profile is active: 'prod'"` 확인 후, `${DB_URL}`이 해석되지 않아 `Driver org.mariadb.jdbc.Driver claims to not accept jdbcUrl, ${DB_URL}` 오류로 **기동 자체가 실패**(의도된 fail-fast — 운영 DB 정보 없이는 절대 뜨지 않음) |
| `prod` 프로파일 + H2로 datasource만 임시 대체(MariaDB 서버 없이 나머지 설정 검증용) | `--spring.profiles.active=prod --spring.datasource.url=jdbc:h2:mem:...` 등 오버라이드 | `ddl-auto: validate`가 실제로 적용되어 빈 스키마에 대해 `Schema validation: missing table [comment]`로 실패(=`create-drop`이 아님을 증명) + 로그에 `"H2 console available"` 문구가 아예 나타나지 않음(=`h2.console.enabled: false` 적용 증명) |

`prod` 프로파일이 실제 MariaDB에 정상 접속해 끝까지 기동하는 것은 이 환경에 운영 DB가 없어
검증하지 못했다 — `ddl-auto: validate`가 스키마 불일치를 정확히 감지해 거부하는 동작과
`h2.console.enabled: false`가 적용되는 것까지는 확인했으므로, 남은 것은 실제 운영 DB 접속
자체(드라이버·네트워크 수준)뿐이다.

## 7.5 실행 방법

### 로컬 실행

```powershell
.\gradlew bootRun
```

프로파일 지정:

```powershell
.\gradlew bootRun --args="--spring.profiles.active=local"
```

### JAR 빌드 후 실행

```powershell
.\gradlew clean build
java -jar build\libs\kraft-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

### 접속 경로

| URL | 설명 |
| --- | --- |
| `http://localhost:8080/` | 게시글 목록 |
| `http://localhost:8080/posts/save` | 게시글 등록 |
| `http://localhost:8080/h2-console` | H2 콘솔 (`spring-boot-h2console`, 설정 활성화 필요) |

> ✅ **위 명령이 모두 정상 동작함을 확인했습니다.** `bootRun`으로 기동 후 `/`, `/posts/save`,
> `/h2-console` 모두 curl로 접속을 확인했습니다([09장 4절](09-implementation-summary.md#4-실제-검증-curl-기반-e2e) 참고).

## 7.6 테스트 실행 — ✅ 통과 (2026-09-09부터 계층별 테스트 44개로 확장)

```powershell
.\gradlew test
```

최초에는 `KraftApplicationTests.contextLoads()` 하나뿐이었으나, 이후 아래 표의 계층별 테스트를
실제로 작성했다(P2-15). `spring-boot-starter-data-jpa-test`는 미선언 상태였다가 이번에 추가했다
(Boot 4.1.1에서 실제 해석되는지 `./gradlew dependencies`로 먼저 확인 후 추가).

| 대상 | 애노테이션 | 활용 스타터 | 실제 파일 |
| --- | --- | --- | --- |
| 서비스 로직 | Mockito 단위 테스트 | (스타터 전이 의존성으로 이미 존재) | `PostServiceTest`, `UserServiceTest` |
| API 컨트롤러 | `@WebMvcTest` + `MockMvc` | `spring-boot-starter-webmvc-test` | `PostApiControllerTest`, `UserApiControllerTest` |
| 인증/인가/CSRF | 실제 `SecurityConfig` `@Import` | `spring-boot-starter-security-test` | 위 컨트롤러 테스트에 포함 |
| DTO 검증 규칙 | `@Valid` 요청 바디 검증 | `spring-boot-starter-validation-test` | 위 컨트롤러 테스트에 포함 |
| 화면 라우팅/렌더링 | `@WebMvcTest` + 뷰 검증 | `spring-boot-starter-thymeleaf-test` | `IndexControllerTest` |
| 리포지토리 | `@DataJpaTest` | `spring-boot-starter-data-jpa-test` (신규 추가) | `PostRepositoryTest`, `UserRepositoryTest` |

`./gradlew test` 실행 결과 **44개 테스트 전부 통과**를 확인했다(이후 댓글/사진 업로드/비밀번호
변경 기능이 추가되며 81개로 늘었다 — 최신 총계는 [09장](09-implementation-summary.md) 참고).
상세 구성, 구현 중 실제로
부딪힌 문제(Boot 4의 테스트 애노테이션 패키지 이동, `@MockBean`→`@MockitoBean`,
`@DataJpaTest`가 `JpaConfig`를 자동 포함하지 않는 점, MockMvc의 예외 전파 방식 등)는
[08장 8.10절](08-issues-and-todo.md#810-추가-구현-p2-15-계층별-테스트-코드-2026-09-09) 참고.

## 7.7 최종 반영된 설정 (2026-09-09, 프로파일 3파일 구성)

최초에는 단일 `application.yml`이었으나, [7.4절](#74-프로파일-분리---해결됨-2026-09-09-추가-구현)에서
`local`/`prod`로 분리했다. 현재 `src/main/resources/`에는 3개 파일이 있다.

| 파일 | 역할 |
| --- | --- |
| `application.yml` | 공통 설정(`spring.application.name`, `spring.profiles.default: local`, `spring.jpa.open-in-view: false`, `spring.session.store-type`, `app.upload.dir`(2026-09-10 추가, 게시글 사진 업로드 저장 경로, P3-5), **`app.base-url`**(2026-09-10 추가, 이메일 인증 링크의 기본 URL, P3-4)) |
| `application-local.yml` | 로컬 개발 기본값(H2, `ddl-auto: create-drop`, H2 콘솔 활성화 등) — 무프로파일 시 자동 적용. `spring.mail.*`은 설정하지 않음(의도적 — 아래 7.8절 참고) |
| `application-prod.yml` | 운영 값(환경변수 기반 DataSource, `ddl-auto: validate`, H2 콘솔 비활성화, **환경변수 기반 `spring.mail.*`**(2026-09-10 추가, P3-4) 등) — `--spring.profiles.active=prod`로 명시 필요 |

각 파일의 전체 내용은 7.4절을 참고. 7.2절에서 지적된 항목 중 `hibernate.dialect` 하드코딩은
**제거**, `datasource`/`ddl-auto`/`session.jdbc.initialize-schema`/`h2.console.enabled`/
`thymeleaf.cache`는 프로파일별로 값을 다르게 **추가**했다. `open-in-view: false`(7.2.6절)도
✅ 2026-09-09에 공통 `application.yml`에 추가로 반영했다.

## 7.8 이메일 발송 설정 (`spring.mail.*`) — ✅ 구현 완료 (2026-09-10, P3-4)

이번 세션에서 유일하게 추가한 의존성인 `spring-boot-starter-mail`과 함께 도입되었다.
`local` 프로파일은 `spring.mail.host`를 아예 설정하지 않는다 — Boot의
`MailSenderAutoConfiguration`은 `spring.mail.host`가 있을 때만 `JavaMailSender` 빈을
생성하므로(jar 역컴파일로 `MailSenderCondition$HostProperty` 확인), 로컬 개발 환경에는
SMTP 서버가 전혀 필요 없다. 대신 `ConsoleEmailSender`(`@Profile("local")`)가 인증 링크를
콘솔에 로그로 남긴다.

```yaml
# application-prod.yml
spring:
  mail:
    host: ${MAIL_HOST}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
app:
  base-url: ${APP_BASE_URL}   # 기본값 없음 — 인증 링크 생성에 필수
```

**`DataSource`와 실패 시점이 다르다는 점이 중요하다.** `DataSource`는 자격 증명이 없으면
컨텍스트 기동 시점에 즉시 실패한다(fail-fast, 7.2.4절). 반면 `JavaMailSenderImpl`은 빈 생성
시점에 연결을 시도하지 않고 `send()`가 호출되는 시점에만 실제로 연결한다 — 즉 `MAIL_*` 환경변수
누락은 애플리케이션이 정상 기동된 뒤 첫 이메일 발송 시점에야 드러난다. 이 차이를
`application-prod.yml`에 주석으로 명시해뒀다.

또한 `EmailVerificationService.sendVerificationEmailSafely()`가 발송 실패 예외를 흡수하므로,
운영 환경에서 `MAIL_*` 자격 증명이 잘못되어도 회원가입 자체는 실패하지 않고 로그만 남는다(08장
8.15절 참고) — 다만 이 경우 사용자는 인증 메일을 받지 못하므로 운영 모니터링에서 반드시 관련
경고 로그를 감시해야 한다.
