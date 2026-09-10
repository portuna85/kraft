# Kraft 전체 파일 분석 및 개선 계획

> **⚠️ 구현 전 시점의 스냅샷 문서입니다 (2026-09-09 작성, Asia/Seoul).**
> 이 문서가 F01~F41로 분석한 시점 이후, P0(빌드 복구)+P1(실사용 가능한 상태) 구현이 완료되었다.
> `./gradlew compileJava`/`test`가 통과하고, 회원가입 → 로그인 → 게시글 CRUD가 curl E2E로
> 검증되었다. 이 문서 본문의 "실행 확인"/"정적 확인"/"검증 필요" 판정은 **모두 구현 전 상태 기준**이므로
> 그대로 신뢰하지 말 것 — 현재 상태는 [`README.md`](README.md)와 [`09-implementation-summary.md`](09-implementation-summary.md)를
> 참고하라. 이 문서는 파일별 근거 추적(F01–F41)이 필요할 때 역사적 기록으로만 유지한다.

분석 기준일: 2026-09-09 (Asia/Seoul)  
대상: `D:\workspace\spring\kraft`  
결과물: 기존 라이브러리를 기준으로 한 파일별 분석, 개선 순서, 검증 계획

## 1. 현재 상태와 분석 범위

프로젝트는 회원과 게시글을 위한 Spring MVC·JPA 계층을 구성하기 시작한 단계다. 게시글 REST API에는 다섯 개의 메서드가 선언되어 있지만, 호출 대상 서비스 메서드와 DTO 내용이 없다. 화면 컨트롤러에는 라우팅이 없으며 HTML은 선언된 Thymeleaf와 다른 Mustache 문법을 사용한다.

Gradle 의존성 조회는 성공했다. 테스트 명령은 `compileJava`에서 오류 10건으로 실패하여 테스트 실행과 애플리케이션 기동 여부는 확인하지 못했다. 이번 작업은 분석 문서 작성이며, 아래의 개선안은 애플리케이션에 적용하지 않았다.

### 1.1 범위와 판정 기준

| 구분 | 수량 | 분석 방법 |
| --- | ---: | --- |
| 루트 빌드·실행·문서·개발 설정 | 8 | 전체 텍스트 읽기, 설정 연결 확인 |
| Gradle Wrapper 설정 및 JAR | 2 | 설정·Manifest·SHA-256 확인, Wrapper 실행 |
| 운영 Java 소스 | 17 | 모든 클래스·필드·메서드 및 호출 관계 확인 |
| Java 테스트 | 1 | 테스트 내용 확인, Gradle 테스트 명령 실행 |
| 리소스 설정·HTML·JavaScript | 7 | 전체 텍스트 읽기, 화면·API·모델 대조 |
| IntelliJ 설정 | 6 | 전체 텍스트 읽기, 공유 설정과 개인 상태 구분 |
| **원본 프로젝트 파일 합계** | **41** | **F01–F41에서 파일별 분석** |

`.gradle`과 `build`는 자동 생성 파일로 별도 분석한다. 이 문서 자체는 위 41개에 포함하지 않는다. 의존성 JAR 전체를 역컴파일한 것은 아니며, 프로젝트 파일 분석과 실제 의존성 그래프·관련 설정 메타데이터 확인을 수행했다.

판정은 다음 세 가지로 구분한다.

- **실행 확인:** 명령 결과로 직접 확인한 성공 또는 실패.
- **정적 확인:** 소스와 설정에 실제 존재하거나 없는 구현.
- **검증 필요:** 컴파일 복구 후 기동·HTTP·DB 테스트로 판단할 사항. 예상 결과를 실제 장애로 단정하지 않는다.

원본 텍스트 40개는 엄격한 UTF-8 디코딩 검증을 통과했다. 초기 PowerShell 기본 인코딩 출력에서 발생한 한글 깨짐은 파일 손상으로 분류하지 않는다. 현재 디렉터리는 Git 저장소로 인식되지 않았으므로 커밋·브랜치 기준 비교는 제공하지 않는다.

## 2. 기존 라이브러리와 실행 환경

### 2.1 빌드 기준

| 항목 | 선언 또는 실행 확인 값 | 해석 |
| --- | --- | --- |
| Java toolchain | 25 | 소스와 IDE 모두 Java 25 기준 |
| 실행 JDK | Temurin 25.0.4.1+1-LTS | 로컬 `java -version` 결과 |
| Gradle Wrapper | 9.7.1 | Wrapper 설정과 실제 실행 결과 일치 |
| Spring Boot 플러그인 | 4.1.1 | 기존 버전 유지 |
| dependency-management 플러그인 | 1.1.7 | 버전 없는 의존성의 관리 버전 적용 |
| 저장소 | Maven Central | 추가 저장소 선언 없음 |
| 테스트 플랫폼 | JUnit Platform | `useJUnitPlatform()` 적용 |

Spring Boot 4.1.1의 공식 지원 범위에 Java 25와 Gradle 9.x가 포함된다. 현재 컴파일 오류를 해결하기 위해 Boot나 JDK를 낮출 근거는 발견하지 못했다. [Spring Boot 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)

### 2.2 직접 의존성

버전은 파일에 명시한 값과 실제 `runtimeClasspath` 또는 `testRuntimeClasspath`에서 해결된 값을 구분했다. 아래 Boot 모듈은 모두 선언에서 버전을 생략하고 실제로 4.1.1이 선택됐다.

| 직접 의존성 | 선언 범위 | 실제 버전 | 현재 용도와 분석 |
| --- | --- | --- | --- |
| `spring-boot-h2console` | implementation | 4.1.1 | H2 콘솔 지원 모듈. 콘솔 활성화·접근 설정은 별도로 선언하지 않음 |
| `spring-boot-starter-security` | implementation | 4.1.1 | 보안 기반. 자체 인증·권한 설정 없음 |
| `spring-boot-starter-thymeleaf` | implementation | 4.1.1 | HTML 템플릿 엔진. 현재 화면 문법과 불일치 |
| `spring-boot-starter-validation` | implementation | 4.1.1 | 입력 검증 기능은 있지만 DTO 제약과 컨트롤러 검증 호출 없음 |
| `spring-boot-starter-webmvc` | implementation | 4.1.1 | MVC·REST·JSON 처리 기반 |
| `thymeleaf-extras-springsecurity6` | implementation | 3.1.5.RELEASE | 보안 조건 출력용. 현재 화면에서 해당 문법을 사용하지 않음 |
| `lombok` | implementation | 1.18.46 | Getter·생성자 어노테이션 사용. annotationProcessor 선언 없음 |
| `spring-boot-starter-data-jpa` | implementation | 4.1.1 | Entity·JpaRepository·감사 필드 기반 |
| `spring-boot-starter-session-jdbc` | implementation | 4.1.1 | JDBC 세션 자동 구성 기반 |
| `h2` | runtimeOnly | 2.4.240 | 임베디드 DB 후보. H2 방언이 설정에 고정됨 |
| `mariadb-java-client` | runtimeOnly | 3.5.10 | 드라이버만 존재. MariaDB 연결 정보·프로파일 없음 |
| `spring-boot-starter-security-test` | testImplementation | 4.1.1 | 보안 테스트 기반. 현재 관련 테스트 없음 |
| `spring-boot-starter-thymeleaf-test` | testImplementation | 4.1.1 | 템플릿 테스트 기반 |
| `spring-boot-starter-validation-test` | testImplementation | 4.1.1 | 검증 테스트 기반 |
| `spring-boot-starter-webmvc-test` | testImplementation | 4.1.1 | MVC 테스트 기반 |
| `junit-platform-launcher` | testRuntimeOnly | 6.0.3 | JUnit Platform 실행기 |

`thymeleaf-extras-springsecurity6`라는 이름에 6이 있다는 사실만으로 Security 7과의 비호환을 확정하지 않는다. 실제 선택 버전과 템플릿 보안 표현식의 실행 결과를 기준으로 판단한다.

### 2.3 주요 전이 의존성과 브라우저 라이브러리

| 라이브러리 | 실제 버전 또는 URL 지정 버전 | 의미 |
| --- | --- | --- |
| Spring Framework | 7.0.9 | MVC·컨텍스트·트랜잭션 등 |
| Spring Security | 7.1.1 | 보안 설정은 이 버전의 API 기준 |
| Spring Data JPA | 4.1.1 | Repository·JPA 통합 |
| Hibernate ORM | 7.4.5.Final | Entity 매핑과 영속성 처리 |
| Jakarta Persistence | 3.2.0 | 기존 `jakarta.persistence` import와 연결 |
| Hibernate Validator | 9.1.3.Final | 기존 Validation starter의 검증 구현 |
| Thymeleaf / thymeleaf-spring6 | 3.1.5.RELEASE | 실제 템플릿 엔진과 Spring 통합 모듈 |
| Jackson Databind | 3.1.5 | 실제 모듈은 `tools.jackson.core:jackson-databind`; 향후 사용자 정의 JSON 코드는 해당 버전 기준 |
| Tomcat Embed | 11.0.24 | 내장 웹 서버 |
| HikariCP | 7.0.2 | JDBC 연결 풀 |
| Spring Session JDBC | 4.1.1 | 세션 저장소 |
| JUnit Jupiter | 6.0.3 | 테스트 import는 `org.junit.jupiter` 유지 |
| Mockito / Mockito JUnit Jupiter | 5.23.0 | 기존 테스트 의존성으로 서비스 단위 테스트 가능 |
| AssertJ | 3.27.7 | 기존 테스트 의존성으로 결과 검증 가능 |
| Bootstrap | 4.3.1 | header/footer의 CDN URL로 로드 |
| jQuery | 3.3.1 | footer의 CDN URL로 로드, AJAX와 이벤트 처리 |

브라우저 라이브러리는 URL의 지정 버전을 확인한 것이며 CDN 응답·브라우저 실행을 검증하지 않았다. 현재 기본 폼과 버튼의 동작을 위해 새로운 프런트엔드 프레임워크를 도입할 필요는 없다.

## 3. 구조와 데이터 흐름

### 3.1 요청 처리 구조

점선은 호출·데이터 연결이 아직 구현되지 않은 부분이다. 이 그림은 소스상의 구조이며 정상 실행이 확인된 흐름은 아니다.

~~~mermaid
flowchart TD
    Browser["브라우저"] --> Routes["/, /posts/save, /posts/update/{id}"]
    Routes -. "라우팅 메서드 없음" .-> Index["IndexController"]
    Index -. "조회 및 Model 구성 없음" .-> Service["PostService"]
    Index -. "뷰 반환 없음" .-> Views["HTML 템플릿: Mustache 문법"]
    Views -. "fragment 전환 필요" .-> Assets["jQuery, Bootstrap, index.js"]
    Assets --> Api["게시글 REST API 5개"]
    Api --> Dto["요청 DTO: 필드 없음"]
    Api -. "호출 대상 메서드 없음" .-> Service
    Service -. "Repository 주입 필드만 존재" .-> Repo["PostRepository"]
    Repo --> Post["Post Entity"]
    Post --> User["User Entity"]
    Service -. "응답 변환 없음" .-> Response["응답 DTO: 필드 없음"]
    Response -. "미완성 응답 경로" .-> Browser
~~~

화면은 `posts`라는 모델을 서버에서 렌더링하려는 구조다. JavaScript에는 목록 GET 호출이 없으므로 목록 조회 API만 완성해도 현재 화면 목록이 자동으로 채워지지는 않는다. `IndexController`에서 조회 결과를 모델에 넣는 연결이 필요하다.

### 3.2 도메인 관계

~~~mermaid
classDiagram
    class BaseEntity {
        LocalDateTime createdAt
        LocalDateTime updatedAt
    }
    class User {
        Long id
        String name
        String email
        String password
        Role role
        List~Post~ posts
        getRoleKey()
    }
    class Post {
        Long id
        String title
        String content
        String picture
        User user
    }
    class Role {
        ADMIN
        USER
        GUEST
    }
    class Comment {
        Long id
        String content
    }
    BaseEntity <|-- User
    BaseEntity <|-- Post
    User "0..1" <-- "0..*" Post : user_id
    User --> Role : role
~~~

`Post.user`가 외래 키를 가진 관계의 주인이다. `User.posts`는 `mappedBy = "user"`로 이를 참조한다. 현재 `optional=false`와 `nullable=false`가 없으므로 작성자 필수 조건은 매핑에 강제되어 있지 않다. `Comment`에는 회원·게시글 연관관계나 감사 필드 상속이 없다.

## 4. 전체 파일별 분석

각 항목의 링크와 줄 번호는 분석 시점의 원본 파일 기준이다. 생성된 코드를 제외하고 실제 선언을 기준으로 설명한다.

### 4.1 빌드·실행·루트 설정

#### F01 — `build.gradle.kts`

- **근거:** [빌드 설정](../build.gradle.kts#L1), Java 설정 11–15행, 의존성 21–38행, 테스트 설정 40–42행.
- **역할·연결:** Java·Boot·의존성 관리 플러그인을 적용하고 운영 및 테스트 classpath를 구성한다. 개별 의존성 버전 대부분을 Boot 관리에 맡긴다.
- **문제:** 28행의 Lombok은 `implementation`에만 있다. annotation processor 설정이 없으며 실제 컴파일에서 `Role` 생성자와 `getKey()` 생성 실패가 드러났다. 생성자 주입을 사용하는 다른 클래스도 같은 설정에 의존한다.
- **개선:** 기존 Lombok을 `compileOnly`와 `annotationProcessor`에 선언한다. 현재 테스트는 Lombok을 사용하지 않으므로 테스트용 processor를 의무적으로 추가할 필요는 없다. 기존 버전 관리와 starter 구성은 유지한다.

#### F02 — `settings.gradle.kts`

- **근거:** [프로젝트 이름](../settings.gradle.kts#L1).
- **역할·연결:** 루트 프로젝트 이름을 `kraft`로 지정한다. 별도 하위 모듈 선언이 없는 단일 프로젝트다.
- **판정·개선:** 현 구조와 일치한다. 현재 문제 해결에 멀티모듈 전환은 필요하지 않다.

#### F03 — `gradlew`

- **근거:** [POSIX 실행 스크립트](../gradlew#L67), Java 선택 119행 이후, Wrapper 실행 211행 이후.
- **역할·연결:** 스크립트 위치와 심볼릭 링크를 처리하고 Java 및 JVM 옵션을 구성한 뒤 Wrapper JAR를 실행한다. POSIX 셸과 `xargs`를 전제로 한다.
- **판정·개선:** Windows 환경에서는 이 스크립트를 실행하지 않았다. 애플리케이션 기능 수정 대상이 아니며 생성 스크립트의 임의 편집은 개선안에 포함하지 않는다.

#### F04 — `gradlew.bat`

- **근거:** [Windows 실행 스크립트](../gradlew.bat#L26), Java 탐색 41행 이후, 실행 78행.
- **역할·연결:** 실행 위치와 Java 경로를 결정하고 `gradle-wrapper.jar`에 명령을 전달한다.
- **판정·개선:** 의존성 조회와 컴파일 태스크 실행으로 동작을 확인했다. 테스트 실패 원인은 애플리케이션 소스 및 Lombok 설정이며 Wrapper 실행 실패가 아니다.

#### F05 — `HELP.md`

- **근거:** [기본 도움말](../HELP.md#L1).
- **역할·연결:** Boot·Gradle·Security·Thymeleaf 등의 공식 문서와 가이드 링크를 모은 초기 안내문이다.
- **문제·개선:** 프로젝트 실행 조건, 현재 오류, DB 선택, 실제 기능 설명은 없다. 링크에 OAuth2나 LDAP 가이드가 있다고 해서 그 기능이 구현된 것은 아니다. 프로젝트별 현황은 본 문서에 기록하며 `.gitignore`가 HELP.md를 제외한다는 점도 구분한다.

#### F06 — `kraft.iml`

- **근거:** [IDE 모듈 파일](../kraft.iml#L2).
- **역할·연결:** IntelliJ Java 모듈 형식과 루트 콘텐츠를 선언한다. 별도 라이브러리와 소스 루트를 상세히 기록하지 않는다.
- **판정·개선:** Gradle import 설정과 함께 해석해야 한다. 명령행 빌드의 기준은 Gradle이며 이 파일만으로 classpath 불량을 단정하지 않는다. 버전 관리 제외 패턴에 포함된다.

#### F07 — `.gitattributes`

- **근거:** [줄바꿈과 바이너리 속성](../.gitattributes#L1).
- **역할·연결:** `gradlew`는 LF, `*.bat`는 CRLF, `*.jar`는 바이너리로 지정한다.
- **판정·개선:** 플랫폼별 Wrapper 파일 처리에 맞는 설정이다. 애플리케이션 인코딩 장애를 입증하는 내용은 없으며 변경 필요를 발견하지 못했다.

#### F08 — `.gitignore`

- **근거:** [제외 규칙](../.gitignore#L1), IntelliJ 규칙 20행 이후.
- **역할·연결:** Gradle 캐시·빌드 결과·IDE 개인 설정을 제외하고 Wrapper JAR의 예외 규칙을 둔다. 소스 아래의 `build` 디렉터리를 재포함하는 규칙도 존재한다.
- **판정·개선:** 일반적인 개발 산출물 구분에 사용된다. 현재 디렉터리에는 Git 메타데이터가 없어 실제 추적 여부는 확인할 수 없다. 본 문서 경로를 제외하는 규칙은 없다.

### 4.2 Gradle Wrapper

#### F09 — `gradle/wrapper/gradle-wrapper.properties`

- **근거:** [배포 설정](../gradle/wrapper/gradle-wrapper.properties#L1).
- **역할·연결:** Gradle 9.7.1 배포 ZIP, 사용자 홈 기준 캐시 경로, 10초 네트워크 제한, 재시도 0회, URL 검증 설정을 지정한다.
- **판정·개선:** 기존 캐시를 사용할 수 있는 환경에서 Wrapper 실행이 성공했다. 새 환경의 최초 다운로드는 별도로 검증하지 않았다. `distributionSha256Sum`은 선언되어 있지 않으며 URL 검증을 배포 파일 해시 검증과 동일하게 해석하지 않는다.

#### F10 — `gradle/wrapper/gradle-wrapper.jar`

- **근거:** [Wrapper 바이너리](../gradle/wrapper/gradle-wrapper.jar)의 Manifest 및 SHA-256.
- **역할·연결:** 두 실행 스크립트가 호출하는 Gradle bootstrap 프로그램이다. Manifest의 Main-Class는 `org.gradle.wrapper.GradleWrapperMain`, Implementation-Title은 `Gradle Wrapper`다.
- **실행 확인:** 실제 Gradle 명령을 시작했다. SHA-256은 `7A9CE74CFF467CA1BF60A4FCD9F05185ACCEDA4D0F382434D393E17864262C5D`다.
- **한계·개선:** 해시는 식별용으로 계산했으며 공식 배포 해시와 대조한 무결성 인증은 수행하지 않았다. 바이너리 내부 전체 구현을 분석했다고 주장하지 않는다.

### 4.3 애플리케이션과 도메인

#### F11 — `src/main/java/com/kraft/KraftApplication.java`

- **근거:** [진입점](../src/main/java/com/kraft/KraftApplication.java#L6).
- **역할·연결:** `@SpringBootApplication`과 `SpringApplication.run`으로 `com.kraft` 이하 구성요소의 애플리케이션을 시작한다.
- **문제·개선:** 진입점 자체의 컴파일 오류는 보고되지 않았다. 프로젝트 전체에 `@EnableJpaAuditing` 설정은 없으므로 감사 기능 활성화를 별도 구성으로 보완하는 방향을 제시한다. 기동 성공은 아직 미검증이다.

#### F12 — `src/main/java/com/kraft/domain/BaseEntity.java`

- **근거:** [감사 기반 클래스](../src/main/java/com/kraft/domain/BaseEntity.java#L12).
- **역할·연결:** `@MappedSuperclass`로 User와 Post에 `createdAt`, `updatedAt`을 전달한다. `AuditingEntityListener`, `@CreatedDate`, `@LastModifiedDate`를 사용한다.
- **문제:** 리스너·필드 선언은 있지만 프로젝트의 감사 활성화가 빠졌다. 날짜 타입은 `LocalDateTime`이며 저장·표시 시간대 정책은 명시되어 있지 않다.
- **개선:** 기존 Spring Data JPA의 감사 설정을 활성화하고 생성·변경 시각의 저장을 테스트한다. 화면의 `modifiedDate`와는 DTO에서 매핑하거나 화면 이름을 함께 맞춘다. [JPA 감사 공식 문서](https://docs.spring.io/spring-data/jpa/reference/auditing.html)

#### F13 — `src/main/java/com/kraft/domain/post/Post.java`

- **근거:** [게시글 Entity](../src/main/java/com/kraft/domain/post/Post.java#L12).
- **역할·연결:** `posts` 테이블에 IDENTITY ID, 필수 제목·내용, 선택 사진 문자열, `user_id` 관계를 선언하고 BaseEntity를 상속한다. 제목 길이는 255, 내용은 SQL `TEXT`다.
- **문제:** 기본 생성자용 Lombok 외에 조회 접근자·생성 메서드·수정 메서드가 없다. 서비스에서 안전하게 생성·변환·수정할 API가 미완성이다. 작성자 관계의 필수 여부와 fetch 전략을 명시하지 않았고 사진 문자열의 의미도 결정되지 않았다.
- **개선:** 기존 Lombok/JPA를 사용해 DTO 변환에 필요한 조회 수단과 제목·내용 변경 메서드를 마련한다. 작성자는 User 관계로 연결한다. JPA가 필드에 접근하므로 Getter 부재 자체를 Entity 매핑 실패라고 보지는 않는다.
- **검증 필요:** 작성자 조회 시 쿼리 수, 대량 목록, 작성자 없는 기존 데이터 처리. N+1 발생은 측정 전까지 확정하지 않는다.

#### F14 — `src/main/java/com/kraft/domain/post/PostRepository.java`

- **근거:** [게시글 Repository](../src/main/java/com/kraft/domain/post/PostRepository.java#L8).
- **역할·연결:** `JpaRepository<Post, Long>` 기본 CRUD와 JPQL `SELECT p FROM Post p ORDER BY p.id DESC`를 제공한다.
- **판정:** 정렬 기준은 수정 시각이 아닌 ID 내림차순이다. 현재 사용자 정의 조회는 전체 목록을 List로 반환하며 페이지 제한이 없다.
- **개선:** 우선 기존 쿼리를 서비스의 목록 조회에 연결한다. 목록 크기와 작성자 조회 비용을 검증한 뒤 필요할 때 기존 Spring Data의 페이징·조회 기능을 사용한다. 임의의 별도 쿼리 라이브러리 도입은 필요하지 않다.

#### F15 — `src/main/java/com/kraft/domain/user/User.java`

- **근거:** [회원 Entity](../src/main/java/com/kraft/domain/user/User.java#L12), 필드 26–45행, `getRoleKey()` 47행.
- **역할·연결:** `users` 테이블에 IDENTITY ID, 이름·이메일·비밀번호·문자열 enum 역할을 저장하고 작성글 역방향 관계를 둔다. BaseEntity를 상속한다.
- **문제:** 주석에는 비밀번호만 수정, 이메일 인증에 따른 GUEST → USER 전환, 공개 게시글 조회가 있지만 이를 실행하는 서비스·보안 코드는 없다. 이메일의 unique 제약도 선언되지 않았다. `getRoleKey()`는 role의 Getter 생성에 의존하며 현재 컴파일 오류가 난다.
- **개선:** 주석의 업무 의도와 실제 구현을 분리한다. 향후 회원 기능에서는 생성·비밀번호 변경·역할 전환을 명시적 동작으로 제공하고 기존 Security의 비밀번호 인코더를 사용한다.
- **주의:** 비밀번호 저장 경로가 구현되지 않았으므로 평문 저장이 실제 발생했다고 판단하지 않는다. 초기화되지 않은 posts 컬렉션과 null role에 대한 사용 규칙은 객체 생성 경로를 마련할 때 함께 검증한다.

#### F16 — `src/main/java/com/kraft/domain/user/Role.java`

- **근거:** [역할 enum](../src/main/java/com/kraft/domain/user/Role.java#L6).
- **역할·연결:** ADMIN·USER·GUEST에 각각 `ROLE_*` 키와 한국어 이름을 부여한다. User의 `getRoleKey()`가 key를 참조한다.
- **실행 확인:** enum 인스턴스 생성 세 곳에서 두 인자를 받는 생성자를 찾지 못했고, User에서 `getKey()`를 찾지 못했다.
- **개선:** enum 항목을 바꾸기 전에 Lombok processor 설정을 복구한다. 역할 선언만으로 URL·객체 소유권 검사가 생기지는 않는다. 비로그인 사용자와 로그인한 GUEST도 별도로 다룰 필요가 있다.

#### F17 — `src/main/java/com/kraft/domain/user/UserRepository.java`

- **근거:** [회원 Repository](../src/main/java/com/kraft/domain/user/UserRepository.java#L5).
- **역할·연결:** `JpaRepository<User, Long>`를 상속하여 기본 영속성 연산을 제공하고 UserService에 주입된다.
- **문제·개선:** 이메일 조회·중복 확인 등 로그인·가입에 특화된 연산은 없다. 회원 기능을 구현할 때 필요한 조회만 기존 Spring Data 파생 메서드로 추가한다. 빈 본문이어도 상속된 Repository 기능은 존재한다.

#### F18 — `src/main/java/com/kraft/domain/comment/Comment.java`

- **근거:** [댓글 Entity](../src/main/java/com/kraft/domain/comment/Comment.java#L9).
- **역할·연결:** `comment` 테이블의 ID와 내용만 선언한 2단계 기능 골격이다.
- **문제:** 게시글·작성자 관계, ID 자동 생성, 감사 필드, Repository·서비스·API·화면이 없다.
- **개선:** 후속 기능으로 분리한다. 자동 생성이 없는 ID는 수동 할당 설계일 수 있으므로 그것만으로 Entity 부팅 실패를 단정하지 않는다. 댓글을 실제 저장할 때 ID 할당 방식과 관계·삭제 정책을 확정해야 한다.

### 4.4 서비스와 컨트롤러

#### F19 — `src/main/java/com/kraft/service/post/PostService.java`

- **근거:** [게시글 서비스](../src/main/java/com/kraft/service/post/PostService.java#L8).
- **역할·연결:** `@Service`, `@RequiredArgsConstructor`, final PostRepository 필드만 있다. PostResponseDto import는 사용하지 않는다.
- **실행 확인:** 컨트롤러에서 호출하는 `save`, `update`, `delete`, `findById`, `findAllDesc`가 모두 없어 다섯 건의 컴파일 오류가 발생했다.
- **개선:** 기존 Repository를 이용하여 다섯 메서드를 구현하고 요청·Entity·응답 변환을 연결한다. 변경 작업의 트랜잭션과 조회 작업의 경계를 정의하고 조회 실패를 일관되게 처리한다. 변경 감지 방식의 수정이라면 트랜잭션 내 영속 Entity를 수정해야 한다.

#### F20 — `src/main/java/com/kraft/service/user/UserService.java`

- **근거:** [회원 서비스](../src/main/java/com/kraft/service/user/UserService.java#L7).
- **역할·연결:** UserRepository를 생성자 주입하려는 서비스 골격이다.
- **문제·개선:** 회원가입·조회·인증·비밀번호 변경·이메일 인증 메서드가 없다. 주입만으로 이런 기능이 제공되지는 않는다. 게시글 작성자 연결에 필요한 인증 사용자 조회와 회원 기능의 전체 구현 범위를 구분한다.

#### F21 — `src/main/java/com/kraft/web/IndexController.java`

- **근거:** [화면 컨트롤러](../src/main/java/com/kraft/web/IndexController.java#L5).
- **역할·연결:** `@Controller`만 선언되어 있다. 서비스 주입, URL 매핑, Model 구성, 뷰 이름 반환은 없다.
- **문제:** 화면에서 사용하는 `/`, `/posts/save`, `/posts/update/{id}`를 이 컨트롤러가 처리하지 않는다.
- **개선:** 기존 MVC를 사용하여 목록·작성·수정 화면의 반환과 `posts`, `post`, 로그인 표시 모델을 연결한다. 템플릿 파일이 있다는 사실을 사용자 정의 컨트롤러 매핑 완료로 해석하지 않는다.

#### F22 — `src/main/java/com/kraft/web/api/PostApiController.java`

- **근거:** [게시글 REST 컨트롤러](../src/main/java/com/kraft/web/api/PostApiController.java#L13).
- **역할·연결:** REST 메서드 다섯 개가 요청 DTO를 받고 PostService에 위임한다. 등록·수정·삭제는 Long, 상세·목록은 DTO 타입을 반환하도록 선언되어 있다.
- **실행 확인:** 15행의 공개 클래스명이 `PostsApiController`로 파일명과 달라 컴파일 오류가 발생했다. 서비스 메서드 누락도 이 파일의 호출에서 드러났다.
- **개선:** 공개 클래스명을 파일명과 같은 `PostApiController`로 정리하는 최소 변경을 제안한다. 기존 경로를 기준으로 DTO·서비스를 연결하고 `@Valid`, 오류 응답 처리와 보안 검증을 보완한다.
- **한계:** HTTP 성공 상태·본문을 실제 호출로 확인한 것이 아니다. 컨트롤러에는 상태 코드 사용자 정의가 없으며 지금의 Long 반환을 근거 없이 201·204 계약으로 바꾸지 않는다.

#### F23 — `src/main/java/com/kraft/web/api/UserApiController.java`

- **근거:** [회원 REST 컨트롤러](../src/main/java/com/kraft/web/api/UserApiController.java#L7).
- **역할·연결:** UserService를 주입하려는 `@RestController`다.
- **문제·개선:** 요청 매핑과 메서드가 전혀 없어 공개 회원 API는 아직 선언되어 있지 않다. 회원 관련 경로나 JSON 계약을 존재하는 API처럼 문서화하지 않는다.

### 4.5 게시글 DTO

#### F24 — `src/main/java/com/kraft/web/dto/post/PostSaveRequestDto.java`

- **근거:** [등록 요청 DTO](../src/main/java/com/kraft/web/dto/post/PostSaveRequestDto.java#L3).
- **역할·연결:** 등록 API의 `@RequestBody` 타입이지만 현재 빈 클래스다. 브라우저는 title·author·content를 보낸다.
- **문제·개선:** 필드·접근자·검증·변환이 없다. 제목·내용을 기존 Validation과 연결하고 작성자 신원을 클라이언트 입력 문자열과 구분해야 한다. 실제 JSON 역직렬화 결과는 컴파일 복구 뒤 검증한다.

#### F25 — `src/main/java/com/kraft/web/dto/post/PostUpdateRequestDto.java`

- **근거:** [수정 요청 DTO](../src/main/java/com/kraft/web/dto/post/PostUpdateRequestDto.java#L3).
- **역할·연결:** 수정 API의 본문 타입이며 빈 클래스다. 브라우저 요청 필드는 title·content이고 ID는 경로에 있다.
- **개선:** 제목·내용과 그 제약을 담고 ID 및 작성자 변경을 본문에 불필요하게 추가하지 않는 방향을 제안한다. 작성자 권한은 화면의 readonly 속성으로 보장할 수 없으므로 서버에서 다뤄야 한다.

#### F26 — `src/main/java/com/kraft/web/dto/post/PostResponseDto.java`

- **근거:** [상세 응답 DTO](../src/main/java/com/kraft/web/dto/post/PostResponseDto.java#L3).
- **역할·연결:** 단건 조회 API의 반환 타입이다. 수정 화면은 post의 id·title·author·content를 요구한다.
- **문제·개선:** Entity 생성자 변환이나 필드가 없다. 필요한 게시글 표시 값만 매핑하고 User Entity 전체를 직렬화하지 않는 방향을 제안한다. password와 양방향 관계 노출은 현재 발생한 사고가 아닌 향후 구현에서 방지할 사항이다.

#### F27 — `src/main/java/com/kraft/web/dto/post/PostsListResponseDto.java`

- **근거:** [목록 응답 DTO](../src/main/java/com/kraft/web/dto/post/PostsListResponseDto.java#L3).
- **역할·연결:** 목록 API의 원소 타입이다. 목록 화면은 id·title·author·modifiedDate를 요구한다.
- **문제·개선:** 필드·변환·접근자가 없다. author는 User의 표시 이름, modifiedDate는 BaseEntity.updatedAt에 대응시키는 개선안을 제시한다. 정렬은 Repository의 ID 내림차순과 맞춘다.

### 4.6 설정과 화면·JavaScript

#### F28 — `src/main/resources/application.yml`

- **근거:** [애플리케이션 설정](../src/main/resources/application.yml#L1).
- **역할·연결:** 애플리케이션 이름, SQL 출력, H2 방언, JDBC 세션 선택 의도를 선언한다.
- **정적 확인:** 데이터소스 URL·자격 증명·프로파일·DDL 정책·사용자 정의 보안 설정은 없다. MariaDB 드라이버가 있어도 이 파일은 MariaDB 접속을 구성하지 않는다.
- **메타데이터 확인:** 설치된 Boot 4.1.1 session/session-jdbc 모듈에서 `spring.session.store-type` 속성을 찾지 못했다. JDBC starter 기반 자동 구성을 기준으로 이 구형 선택 속성의 정리를 제안한다. [Spring Session 자동 구성](https://docs.spring.io/spring-boot/reference/web/spring-session.html)
- **기본값과 개선:** JDBC 세션 메타데이터의 `initialize-schema` 기본값은 `embedded`, 테이블 이름은 `SPRING_SESSION`이다. 실제 테이블 생성은 미검증이다. H2 개발 환경과 MariaDB 환경의 데이터소스·방언·세션 스키마를 구분하고, 운영 DB에서 임의의 스키마 재생성을 기본안으로 삼지 않는다.

#### F29 — `src/main/resources/templates/index.html`

- **근거:** [목록 화면](../src/main/resources/templates/index.html#L1), 로그인 영역 8–15행, 목록 30–37행.
- **역할·연결:** 등록 링크, 로그인 상태 표시, OAuth2 로그인 링크, 게시글 목록과 수정 링크를 보여주려는 화면이다.
- **문제:** include·조건·반복·변수 표현식이 Mustache 문법이다. 기존 Thymeleaf 구성과 맞지 않는다. posts·userName 공급 경로가 없고 author·modifiedDate도 DTO에 없다.
- **개선:** 기존 Thymeleaf의 `th:replace`, `th:if`/`th:unless`, `th:each`, `th:text`, `th:href`로 연결하는 방향을 제시한다. 로그인 링크는 실제 제공 인증 방식과 맞춰야 한다. Google·Naver 링크만으로 OAuth2가 구현된 것은 아니다. [Thymeleaf 공식 문서](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html)

#### F30 — `src/main/resources/templates/layout/header.html`

- **근거:** [공통 헤더](../src/main/resources/templates/layout/header.html#L1).
- **역할·연결:** DOCTYPE·html·head·body 시작 태그, 제목·UTF-8 메타, Bootstrap CSS를 둔다.
- **문제:** Thymeleaf fragment 선언이 없고 본문을 다른 파일에서 닫는 Mustache partial 구조다. `lang`과 모바일 viewport 메타도 없다.
- **개선:** 각 페이지의 유효한 HTML 구조를 유지하면서 head·공통 영역을 Thymeleaf fragment로 만든다. 한국어 언어 속성, viewport, AJAX에 전달할 CSRF 메타를 연결한다. Bootstrap 버전 변경은 기본안에 포함하지 않는다.

#### F31 — `src/main/resources/templates/layout/footer.html`

- **근거:** [공통 스크립트](../src/main/resources/templates/layout/footer.html#L1).
- **역할·연결:** jQuery → Bootstrap → index.js 순서로 로드하고 body·html을 닫는다. index.js의 jQuery 의존 순서는 맞는다.
- **문제·개선:** partial 구문이 처리되지 않으면 다른 페이지에 스크립트가 포함되지 않는다. Thymeleaf fragment로 전환하고 애플리케이션 리소스 URL도 템플릿 URL 표현식과 맞춘다.
- **검증 필요:** CDN 요청, 콘솔 오류, 실제 사용할 Bootstrap 상호작용 구성요소. 현재 정적 폼만 보고 별도 브라우저 의존성 누락을 확정하지 않는다.

#### F32 — `src/main/resources/templates/post/post-save.html`

- **근거:** [등록 화면](../src/main/resources/templates/post/post-save.html#L1), 입력 8–18행, 버튼 22행.
- **역할·연결:** title·author·content 입력과 `btn-save` 버튼을 제공한다. JS가 이를 읽어 JSON을 전송한다.
- **문제:** Mustache include, 필수·길이 제약 및 필드별 오류 표시 부재, 자유 입력 author와 User 관계의 불일치가 있다.
- **개선:** Thymeleaf fragment와 검증 표시를 연결한다. 현재 버튼은 type=button이며 AJAX 제출이므로 action 부재만을 고장 원인으로 단정하지 않는다. 작성자 입력 정책은 인증 사용자 연결과 함께 정리한다.

#### F33 — `src/main/resources/templates/post/post-update.html`

- **근거:** [수정 화면](../src/main/resources/templates/post/post-update.html#L1), 입력 9–22행, 수정·삭제 버튼 26–27행.
- **역할·연결:** post의 ID·제목·작성자·내용을 표시하고 JS가 수정·삭제 요청을 보낸다.
- **문제:** 데이터와 include가 Mustache 문법이며 단건 모델 공급이 없다. 9행의 글 번호 label이 `for="title"`로 잘못 연결돼 있다.
- **개선:** `th:value`·`th:text`와 fragment로 변경하는 방향을 제시하고 글 번호 label을 id와 맞춘다. 버튼 표시 조건과 서버 측 소유권 검사를 별도로 마련한다. readonly는 접근 제어 수단이 아니다.

#### F34 — `src/main/resources/static/js/app/index.js`

- **근거:** [AJAX 및 이벤트 처리](../src/main/resources/static/js/app/index.js#L1), 등록 16행, 수정 36행, 삭제 57행.
- **역할·연결:** 세 버튼에 클릭 핸들러를 등록한다. 등록은 title·author·content, 수정은 title·content, 삭제는 ID 경로만 보낸다. 성공 시 알림 후 루트로 이동한다.
- **문제:** 변경 요청에 CSRF 토큰을 전달하지 않는다. 중복 클릭 제어와 필드별 입력 검증·오류 표시가 없고 실패 시 jqXHR 전체를 JSON 문자열로 알린다. 목록 조회 API는 호출하지 않는다.
- **개선:** 기존 jQuery AJAX에 CSRF 헤더와 요청 진행 상태를 연결하고 서버 오류 응답을 사용자 메시지로 변환한다. 서버의 Long JSON 응답 선언과 `dataType: 'json'`은 함께 유지·검증한다. CSRF 기본 보호에 대한 예상 거부는 HTTP 재현 전까지 검증 필요로 분류한다. [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

### 4.7 테스트

#### F35 — `src/test/java/com/kraft/KraftApplicationTests.java`

- **근거:** [컨텍스트 테스트](../src/test/java/com/kraft/KraftApplicationTests.java#L6).
- **역할·연결:** `@SpringBootTest`의 `contextLoads()` 한 개가 있다. 별도 assertion이 없어도 컨텍스트를 로드하지 못하면 실패하는 의미 있는 기동 테스트다.
- **실행 확인:** 앞선 운영 소스 컴파일 실패로 테스트 메서드는 실행되지 않았다.
- **개선:** 컴파일 복구 후 먼저 이 테스트를 실행한다. CRUD·정렬·검증·보안·템플릿·세션에 대한 검증은 별도 후속 테스트로 보완한다. 기존 starter가 제공하는 JUnit·Mockito·AssertJ를 사용한다.

### 4.8 IntelliJ 설정

#### F36 — `.idea/.gitignore`

- **근거:** [IDE 내부 제외 규칙](../.idea/.gitignore#L1).
- **역할·연결:** shelf·workspace.xml·HTTP 요청 기록·로컬 데이터소스 파일 등을 제외한다. 루트 .gitignore의 .idea 제외와 일부 중복된다.
- **판정·개선:** 개인 상태를 분리하려는 설정이다. 애플리케이션 실행 오류와 직접 관련된 문제를 발견하지 못했다.

#### F37 — `.idea/AndroidProjectSystem.xml`

- **근거:** [프로젝트 시스템 설정](../.idea/AndroidProjectSystem.xml#L3).
- **역할·연결:** IDE의 AndroidProjectSystem 컴포넌트에 Gradle provider ID를 기록한다.
- **판정·개선:** 이 파일명만으로 Android 앱이라고 판단할 수 없다. 실제 빌드에는 Android 플러그인이 없으며 Java·Spring Boot 프로젝트다. 분석을 위해 삭제하거나 수정할 이유는 없다.

#### F38 — `.idea/compiler.xml`

- **근거:** [IDE 컴파일러 설정](../.idea/compiler.xml#L3).
- **역할·연결:** 바이트코드 대상 25와 javac의 `-parameters` 옵션을 기록한다.
- **문제·개선:** Java 버전은 Gradle toolchain과 일치한다. 이 IDE 옵션을 근거로 Gradle의 annotation processor가 설정됐다고 판단할 수 없다. 컴파일 복구는 Gradle 설정을 기준으로 한다.

#### F39 — `.idea/gradle.xml`

- **근거:** [Gradle 연결 설정](../.idea/gradle.xml#L3).
- **역할·연결:** 프로젝트 루트의 Gradle 프로젝트와 모듈을 IDE에 연결한다.
- **판정·개선:** 외부 프로젝트 경로는 프로젝트 디렉터리를 가리킨다. 별도 의존성 정의가 아니며 실제 라이브러리는 Gradle 그래프를 기준으로 분석한다.

#### F40 — `.idea/misc.xml`

- **근거:** [프로젝트 SDK 설정](../.idea/misc.xml#L7).
- **역할·연결:** JDK 25·언어 수준 JDK_25·IDE 출력 디렉터리와 프레임워크 탐지 제외 상태를 기록한다.
- **판정·개선:** Gradle의 Java 25 선언과 일치한다. IDE SDK 이름만으로 실제 실행 JDK를 확정하지 않고 java 실행 결과도 함께 확인했다.

#### F41 — `.idea/workspace.xml`

- **근거:** [IDE 작업 상태](../.idea/workspace.xml#L1), `RunManager`·`TaskManager` 등 컴포넌트.
- **역할·연결:** 자동 import 정책, 편집·AI 제안 통계, 프로젝트 뷰, 개인 속성, 작업 시간, 실행 구성을 기록한다. 실행 구성의 메인 클래스는 `com.kraft.KraftApplication`, 모듈은 `kraft.main`이다.
- **판정·개선:** 실행 대상은 소스의 진입점과 일치한다. 개인 상태와 도구 통계는 업무 요구사항이나 애플리케이션 설정으로 해석하지 않는다. 공유 실행 방법은 Gradle 명령으로 설명하며 개인 값 전체를 보고서에 복제하지 않는다.

### 4.9 자동 생성 파일

| 경로·종류 | 역할 | 분석 결과와 취급 |
| --- | --- | --- |
| `.gradle/9.7.1/checksums` | 의존성 체크섬 캐시·잠금 | 파일 목록·크기를 확인. 앱 소스가 아니며 바이너리 내부 해석은 생략 |
| `.gradle/9.7.1/executionHistory` | 태스크 실행 이력 | 빌드 실행에 따라 바뀌는 캐시 |
| `.gradle/9.7.1/fileHashes`, `fileChanges` | 변경 감지·증분 빌드 | 파일 해시·리소스 해시·상태 데이터 |
| `.gradle/9.7.1/gc.properties`, `.gradle/vcs-1/gc.properties` | 캐시 정리 상태 | 분석 당시 빈 속성 파일 |
| `.gradle/buildOutputCleanup` | 출력 정리 이력·잠금 | cache.properties에서 Gradle 9.7.1 확인 |
| `build/reports/problems/problems-report.html` | Gradle 진단 보고서 | 실패한 compileJava 명령이 생성한 결과. 재빌드 시 교체될 수 있음 |
| 기타 `build` 하위 태스크 산출물 | 컴파일·검사 출력 | 존재하더라도 성공한 실행 파일·통과한 테스트 증거로 간주하지 않음 |

프로젝트 밖 Gradle 사용자 캐시에서는 의존성 해석 결과와 Boot session/session-jdbc JAR의 설정 메타데이터를 확인했다. 그 캐시 전체를 프로젝트 원본 파일로 포함하지 않는다.

## 5. API와 데이터 계약의 현황

### 5.1 현재 선언된 REST API

다음은 소스에 선언된 계약이다. 컴파일 실패 상태이므로 호출 가능한 API 목록이나 실측 HTTP 응답 표가 아니다.

| 메서드 | 경로 | 입력 선언 | 반환 선언 | 현재 단절 지점 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/posts` | PostSaveRequestDto | Long | 요청 필드 및 save 메서드 없음 |
| PUT | `/api/v1/posts/{id}` | Long id, PostUpdateRequestDto | Long | 요청 필드 및 update 메서드 없음 |
| DELETE | `/api/v1/posts/{id}` | Long id | Long id | delete 메서드 없음 |
| GET | `/api/v1/posts/{id}` | Long id | PostResponseDto | findById 메서드와 응답 필드 없음 |
| GET | `/api/v1/posts/list` | 없음 | List<PostsListResponseDto> | findAllDesc 서비스와 응답 변환 없음 |

예외 변환을 담당하는 `@ExceptionHandler`·ControllerAdvice는 없다. 등록·수정 파라미터에 `@Valid`도 없다. `UserApiController`에는 공개 메서드·경로 선언이 없다.

### 5.2 화면과 도메인의 대응

| 소비 지점 | 현재 요구 값 | 실제 모델 상태 | 기존 구조를 이용한 개선 제안 |
| --- | --- | --- | --- |
| 등록 요청 | title, author, content | 등록 DTO 비어 있음 | title·content를 DTO로 받고 작성자 신원은 인증 사용자와 연결 |
| 수정 요청 | title, content / 경로 id | 수정 DTO 비어 있음 | 변경 가능한 필드와 경로 ID를 분리 |
| 목록 모델 | posts | IndexController 비어 있음 | 서비스의 목록 응답을 Model에 저장 |
| 상세·수정 모델 | post | 단건 조회·뷰 매핑 없음 | 응답 DTO를 조회해 Model에 저장 |
| 작성자 표시 | author | Post.user / User.name | 응답의 표시 이름으로 변환 |
| 최종 수정일 | modifiedDate | BaseEntity.updatedAt | 기존 화면 이름에 맞춘 명시적 DTO 매핑 |
| 로그인 표시 | userName | 인증 사용자 조회 없음 | 실제 인증 principal에서 표시 값 도출 |
| 사진 | 화면 입력 없음 | Post.picture만 있음 | URL·저장 키·업로드 여부는 후속 요구사항으로 구분 |

개선 제안은 아직 적용된 JSON 계약이 아니다. 기존 경로와 Long 반환을 보존하는 방향을 기본으로 하며, 등록 요청 author를 신원 결정에 사용하지 않는 변경은 클라이언트 호환 영향으로 기록한다. 비밀번호·전체 User 객체를 게시글 응답에 추가하지 않는다.

## 6. 문제 분류와 개선 순서

### 6.1 컴파일 오류 10건의 근거

| 오류 그룹 | 위치 | 오류 수 | 직접 원인 |
| --- | --- | ---: | --- |
| 공개 클래스명 | F22, 15행 | 1 | PostApiController.java 안에 public PostsApiController 선언 |
| enum 생성자 | F16, 10–12행 | 3 | 두 인자를 받는 Lombok 생성자가 생성되지 않음 |
| enum Getter | F15, 48행 | 1 | Role.getKey()를 찾을 수 없음 |
| 서비스 호출 | F22, 21·26·31·37·42행 | 5 | PostService의 다섯 메서드가 없음 |
| **합계** | | **10** | **이번 compileJava 실행에서 보고된 수량** |

10건은 해당 실행의 진단 수다. 일부 오류를 고친 뒤 추가 오류가 나타나지 않는다는 보장은 없다. 반환값을 상수로 채우는 임시 서비스 구현을 기능 완성으로 취급하지 않는다.

### 6.2 개선 작업의 선후 관계

| 우선순위 | 대상과 구체적 방향 | 완료 판단 |
| --- | --- | --- |
| P0-1 | F01 Lombok 의존성 범위·processor, F22 클래스명 정리 | 관련 생성자·Getter·파일명 오류 해소 |
| P0-2 | F13·F19·F24–F27의 생성·변경·DTO 변환·다섯 서비스 메서드 연결 | compileJava 성공, 호출 대상 존재 |
| P1-1 | F11·F12·F28 감사와 DB·JDBC 세션 설정 정리 | H2 컨텍스트 로딩 및 실제 감사 필드·세션 테이블 검증 |
| P1-2 | F21·F29–F34 화면 라우팅·Thymeleaf·AJAX 데이터 연결 | 목록·등록·수정 화면 렌더링과 API 요청 정합성 확인 |
| P1-3 | 기존 Validation·Security·MVC로 입력 검증·예외·CSRF·접근 권한 연결 | 오류 응답, 토큰, 역할·소유권 검증 완료 |
| P2 | 목록 쿼리 수·DB 환경 분리·사용자 오류 메시지·중복 제출 개선 | 대표 데이터 및 브라우저 시나리오 검증 |
| 후속 기능 | 회원 전체 흐름·이메일 인증·OAuth2·댓글·사진 | 별도 요구사항과 수용 기준 확정 후 구현 |

P0-2의 서비스 구현과 P1-3의 작성자 인증·권한 연결은 함께 검증해야 한다. 컴파일이 먼저 통과해도 권한 정책이 없는 쓰기 기능을 완성된 공개 서비스로 취급하지 않는다.

Lombok 설정의 최소 개선 예시는 다음과 같다. 기존 `implementation("org.projectlombok:lombok")`을 대체하는 제안이며 신규 라이브러리·새 버전 도입이 아니다.

~~~kotlin
compileOnly("org.projectlombok:lombok")
annotationProcessor("org.projectlombok:lombok")
~~~

이는 Lombok 공식 Gradle 설정 방식에 따른다. 테스트 소스에서도 Lombok을 사용하게 될 때만 테스트 범위 설정을 추가한다. [Lombok Gradle 설정](https://projectlombok.org/setup/gradle)

### 6.3 보안·영속성 검토의 경계

- **인증과 권한:** Security starter는 있지만 사용자 정의 SecurityFilterChain·UserDetailsService·PasswordEncoder bean은 없다. User.role 및 주석의 공개 조회 정책이 요청 처리에 연결되어 있지 않다.
- **CSRF와 로그아웃:** AJAX에는 토큰 전달이 없고 로그아웃은 GET 링크다. 기존 보안 기능을 유지하며 CSRF 토큰을 붙이는 쓰기 요청과 POST 로그아웃으로 연결하는 방향을 제안한다. 실제 현재 HTTP 결과는 기동 후 검증해야 한다. [Spring Security CSRF와 로그아웃 보호](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- **작성자와 소유권:** 일반 사용자의 작성·수정·삭제 의도는 주석에 있지만 본인 글만 수정할지, 관리자가 어디까지 개입할지는 정의되지 않았다. 보안 정책을 임의로 구현했다고 보고하지 않는다.
- **JPA 감사:** 필드·리스너와 활성화 설정을 구분한다. 날짜 감사만 구현하는 데 작성자 감사용 AuditorAware를 의무적으로 새로 도입할 필요는 없다. [Spring Data JPA 감사](https://docs.spring.io/spring-data/jpa/reference/auditing.html)
- **DB 선택:** H2 방언 고정과 MariaDB 드라이버 공존 자체를 실행 오류로 확정하지 않는다. MariaDB를 선택하는 환경에서는 연결·방언·스키마 정책이 함께 맞아야 한다.
- **세션:** starter와 메타데이터를 근거로 자동 구성 의도를 확인했지만 실제 세션 저장·만료·테이블 생성은 미검증이다. 로그인이 완성되었다고 보고하지 않는다.

### 6.4 후속 기능의 미정 요구사항

| 기능 | 현재 근거 | 후속 구현 전에 필요한 정의 |
| --- | --- | --- |
| 가입·로그인·비밀번호 변경 | User 필드·주석, 빈 회원 계층 | 로그인 식별자, 이메일 중복 정책, 비밀번호 규칙, 변경 시 본인 확인 |
| 이메일 인증 | User 주석의 GUEST → USER | 토큰 수명·재전송·발송 수단·인증 실패 처리. 현재 메일 기능 선언 없음 |
| 소셜 로그인 | index의 Google·Naver 링크 | 지원 제공자·계정 연결·외부 설정. 현재 OAuth2 client starter 및 등록 설정 없음 |
| 댓글 | Comment의 2단계 주석 | 게시글·회원 관계, ID 할당, 권한, 삭제 정책 |
| 사진 | Post.picture 문자열 | URL/키 저장인지 직접 업로드인지, 개수·저장소·접근 범위 |

이 항목들은 추가 기능의 결정 사항이다. 현재 요청된 분석 문서 작성의 완료를 막는 질문으로 사용하지 않으며, 본 작업에서 라이브러리 추가·외부 연동을 수행하지 않는다.

## 7. 검증 기록과 후속 테스트

### 7.1 실제 수행한 검증

| 검사 | 결과 | 해석과 한계 |
| --- | --- | --- |
| 파일 목록 및 전체 텍스트 읽기 | 원본 41개 식별 | F01–F41에 대응. JAR는 메타데이터·해시 검사 |
| 엄격한 UTF-8 디코딩 | 원본 텍스트 40개 통과 | 한글 깨짐을 소스 손상으로 분류하지 않음 |
| `java -version` | Java 25.0.4.1 | 로컬 실행 환경 확인 |
| `git status --short` | Git 저장소가 아님 | 버전 관리 이력 확인 불가 |
| `gradlew.bat dependencies --configuration runtimeClasspath` | 성공 | 운영 의존성 해석 성공, 애플리케이션 실행 성공을 뜻하지 않음 |
| `gradlew.bat dependencies --configuration testRuntimeClasspath` | 성공 | 기존 JUnit·Mockito·AssertJ 등 해결 버전 확인 |
| `gradlew.bat test` | compileJava 실패, 오류 10건 | 테스트 메서드 실행 전 중단 |
| Wrapper Manifest·SHA-256 | 확인 완료 | 공식 배포와 해시 대조는 미실시 |
| Boot 세션 설정 메타데이터 | 확인 완료 | 속성 목록·기본값 확인, DB 동작 미검증 |
| HTTP·브라우저·MariaDB 검증 | 미실시 | 컴파일 실패로 기동 검증에 진입하지 못함 |

명령은 프로젝트 루트의 PowerShell에서 다음과 같이 재현한다.

~~~powershell
java -version
.\gradlew.bat dependencies --configuration runtimeClasspath --no-daemon --console=plain
.\gradlew.bat dependencies --configuration testRuntimeClasspath --no-daemon --console=plain
.\gradlew.bat test --no-daemon --console=plain
~~~

컴파일 복구 후에는 다음 순서로 진행한다. 아래 명령은 현재 성공한 기록이 아닌 후속 검증 순서다.

~~~powershell
.\gradlew.bat compileJava --no-daemon --console=plain
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat bootRun --console=plain
~~~

### 7.2 후속 수정의 테스트 시나리오

| 영역 | 시나리오 | 수용 기준 |
| --- | --- | --- |
| 빌드 | 기존 Wrapper와 Java 25로 컴파일 | 파일명·생성 코드·누락 메서드 오류 없음 |
| 기동 | 기존 contextLoads, H2 테스트 환경 | 컨텍스트와 Entity·Repository 구성 성공 |
| 등록 | 유효한 제목·내용·인증 사용자 | 새 ID 반환, 조회 가능한 저장 내용과 작성자 일치 |
| 조회·정렬 | 빈 목록, 여러 게시글, 단건 조회 | 빈 목록 처리, ID 내림차순, DTO 표시 값 일치 |
| 수정 | 존재하는 글의 제목·내용 수정 | ID·작성자 보존, 내용과 수정 시각 반영 |
| 삭제·없는 ID | 정상 삭제 및 존재하지 않는 대상 | 삭제 반영, 단건 부재를 명시적 오류로 처리. 반복 삭제 계약도 문서화 |
| 입력 검증 | 공백 제목·내용, 제목 255자와 256자, 잘못된 JSON | 유효 경계는 성공하고 잘못된 입력은 처리 가능한 400 오류 |
| API 오류 | 부재·검증·권한 오류 | 성공 응답과 구분 가능한 일관된 오류 처리, 내부 예외 직접 노출 방지 |
| 화면 | 목록·작성·수정, 한글·특수문자·빈 목록 | Mustache 구문 잔류 없음, fragment·값·링크 정상 처리 |
| 텍스트 출력 | 제목·내용에 HTML 특수문자 | 의도한 일반 텍스트 출력과 escaping 확인 |
| CSRF | 토큰 없음·잘못된 토큰·유효 토큰 | 쓰기 요청의 토큰 검증 유지, 유효한 요청만 이후 권한 검사 진행 |
| 역할·소유권 | 비로그인, GUEST, USER, ADMIN, 타인 글 | 승인된 정책대로 서버가 제어. 버튼 숨김만으로 통과시키지 않음 |
| 로그아웃 | CSRF를 포함한 로그아웃 요청 | 세션 인증 종료 및 이후 접근 정책 적용 |
| 감사 | 최초 저장, 후속 변경·flush·재조회 | 생성 시각 보존 및 갱신 시각 반영 |
| JDBC 세션 | 세션 생성·재요청·만료 또는 무효화 | DB 저장·재사용·종료와 테이블 초기화 확인 |
| DB 환경 | H2와 별도 MariaDB 설정 | 각 DB의 연결·방언·스키마 정책 일치. MariaDB는 실제 환경이 있을 때 수행 |
| 브라우저 동작 | CDN 로딩, 중복 클릭, 서버 오류 | JS 로드 순서·이벤트 동작, 중복 요청 제어, 이해 가능한 오류 표시 |
| 목록 성능 | 여러 글이 작성자를 참조하는 데이터 | 실제 SQL 수와 응답 시간을 측정해 추가 최적화 필요 여부 판단 |

새 테스트 도구를 도입하지 않고 기존 JUnit·Mockito·AssertJ·MVC/Security 테스트 기능을 우선 사용한다. JPA 통합 검증은 현재 존재하는 SpringBootTest 기반으로 계획하며, 별도 JPA 테스트 slice 모듈이 이미 선언되었다고 가정하지 않는다. 위 테스트는 이번 문서 작성 작업에서 새로 구현하거나 통과시킨 것이 아니다.

## 8. 문서 완료 기준

- 원본 41개 파일이 F01–F41에 각각 한 번씩 분석 항목으로 존재한다.
- 파일별 역할·의존 관계·근거 위치·문제 또는 정상 판단·개선 방향이 기록되어 있다.
- 실제 해결 버전과 단순 선언, 주석의 의도와 구현, 실행 오류와 미검증 위험을 구분한다.
- 기존 API 5개와 화면·DTO·Entity 필드 대응 및 개선 순서를 기록한다.
- 원본 소스·설정·라이브러리·DB 스키마를 변경하지 않고 이 문서만 추가한다. 검증 명령이 생성·갱신하는 캐시와 빌드 보고서는 별도로 취급한다.
- 문서의 로컬 파일 링크·파일 수·UTF-8·원본 파일 해시를 검사하고, 실행하지 않은 테스트를 성공으로 표시하지 않는다.

### 8.1 최종 문서 검증 결과

문서 작성 후 자동 대조를 수행했다.

| 검사 | 결과 |
| --- | --- |
| 원본 목록과 F01–F41 경로 대조 | 41개 일치, 누락·추가·중복 0개 |
| 로컬 링크 대상 및 근거 줄 번호 범위 | 오류 0개 |
| 문서 UTF-8 디코딩 | 통과 |
| Markdown 코드 블록 구분자 | 5쌍 일치, Mermaid 2개 포함 |
| 작성 전후 원본 41개 SHA-256 비교 | 변경 0개 |

링크·블록 검증은 정적 검사다. Mermaid 렌더러나 브라우저의 실제 화면 검증을 수행한 것은 아니다. 문서만 추가했으므로 애플리케이션 테스트를 반복 실행하지 않았으며, 7.1절의 컴파일 실패 기준선을 유지한다.
