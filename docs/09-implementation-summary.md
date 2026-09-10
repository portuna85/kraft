# 09. 구현 요약 (P0+P1+P2+P3 전 항목 완료, 소셜 로그인만 사용자 결정으로 제외)

이 문서는 `01~08` 문서에서 지적된 문제들을 실제로 수정한 뒤의 **현재 상태**를 정리한다.
분석 당시(구현 전) 상태는 각 문서의 서술과 [`08-issues-and-todo.md`](08-issues-and-todo.md)에 남아 있으며,
이 문서는 그 이후 적용된 변경 사항과 실제 검증 결과를 기록한다. 구현 계획 원본(P0+P1)과 이후
P2/P3 각 항목의 계획은 `C:\Users\portu\.claude\plans\lazy-rolling-hennessy.md`에 누적되어 있다.

## 1. 범위

사용자가 확정한 최초 범위는 **P0(빌드 복구) + P1(실사용 가능한 상태)**이다. 다음은 최초 구현에서
**명시적으로 제외**했었으나, 이후 사용자 요청으로 P2 항목 일부를 추가 구현했다:

- ~~페이징~~ → **2026-09-09 추가 구현** (P2-10, 7절)
- ~~작성자 본인/`ADMIN` 권한 검증~~ → **2026-09-09 추가 구현** (P2-4, 6절)
- ~~컨트롤러 전역 예외 처리(`@RestControllerAdvice`)~~ → **2026-09-09 추가 구현** (P1-12, 8절)
- ~~`local`/`prod` 프로파일 분리~~ → **2026-09-09 추가 구현** (P2-14, 9절)
- ~~CDN 무결성 속성(`integrity`/`crossorigin`)~~ → **2026-09-09 추가 구현** (P2-13, 10절)
- ~~계층별 테스트 코드~~ → **2026-09-09 추가 구현** (P2-15, 11절)
- ~~`group = "com"` → `com.kraft`~~ → **2026-09-09 추가 구현** (P2-8, 12절)
- ~~`open-in-view`(P2-7)/`User.posts`(P2-6)/`Post.picture` 길이(P2-9)/`/api/v1/posts/list` 경로(P2-11)~~ → **2026-09-09 추가 구현** (13절) — **P2 표 전 항목 해결**
- ~~댓글(Comment) 기능~~ → **2026-09-10 추가 구현** (P3-1~3, 14절)
- ~~회원가입 HTML 화면~~ → **2026-09-10 추가 구현** (P3-7, 14절)
- ~~게시글 사진 업로드~~ → **2026-09-10 추가 구현** (P3-5, 15절)
- ~~비밀번호 변경 HTML 화면~~ → **2026-09-10 추가 구현** (P3-9, 15절)
- **소셜 로그인은 사용자가 명시적으로 제외를 결정**(2026-09-10, "OAuth 인증방식은 제거한다") —
  코드상 애초에 도입된 적이 없어(P0/P1에서 이미 `/login`으로 대체) 실제로 제거할 코드는 없었음
- 실제 이메일 인증 토큰 발급/메일 발송(`spring-boot-starter-mail` 신규 필요) — 유일하게 남은 P3 항목

## 2. 변경된 파일

### 신규 파일

| 경로 | 역할 |
| --- | --- |
| `src/main/java/com/kraft/config/JpaConfig.java` | `@EnableJpaAuditing` — `createdAt`/`updatedAt` 자동 기록 활성화 |
| `src/main/java/com/kraft/config/security/SecurityConfig.java` | `SecurityFilterChain`, `PasswordEncoder` 빈 |
| `src/main/java/com/kraft/config/security/UserDetailsServiceImpl.java` | `UserRepository.findByEmail` 기반 인증 |
| `src/main/java/com/kraft/web/dto/user/SignUpRequestDto.java` | 회원가입 요청 (record) |
| `src/main/java/com/kraft/web/dto/post/PostsPageResponseDto.java` | 게시글 목록 페이징 응답 (record, 2026-09-09 추가 — 7절) |
| `src/main/java/com/kraft/web/exception/ApiExceptionHandler.java` | REST API 전역 예외 처리기 (`ProblemDetail`, 2026-09-09 추가 — 8절). **같은 날 13절**: `MethodArgumentTypeMismatchException` 핸들러(400) 추가 |
| `src/main/resources/application-local.yml` | 로컬 개발 프로파일 값 (2026-09-09 추가 — 9절) |
| `src/main/resources/application-prod.yml` | 운영 프로파일 값, 환경변수 기반 DataSource (2026-09-09 추가 — 9절) |
| `src/test/java/com/kraft/service/post/PostServiceTest.java` | `PostService` Mockito 단위 테스트 10개 (2026-09-09 추가 — 11절) |
| `src/test/java/com/kraft/service/user/UserServiceTest.java` | `UserService` Mockito 단위 테스트 5개 (2026-09-09 추가 — 11절) |
| `src/test/java/com/kraft/domain/post/PostRepositoryTest.java` | `PostRepository` `@DataJpaTest` 4개 (2026-09-09 추가 — 11절) |
| `src/test/java/com/kraft/domain/user/UserRepositoryTest.java` | `UserRepository` `@DataJpaTest` 5개 (2026-09-09 추가 — 11절) |
| `src/test/java/com/kraft/web/api/PostApiControllerTest.java` | `PostApiController` `@WebMvcTest` 8개 (2026-09-09 추가 — 11절) |
| `src/test/java/com/kraft/web/api/UserApiControllerTest.java` | `UserApiController` `@WebMvcTest` 5개 (2026-09-09 추가 — 11절) |
| `src/test/java/com/kraft/web/IndexControllerTest.java` | `IndexController` `@WebMvcTest` 6개, `page=-1` 회귀 방지 포함 (2026-09-09 추가 — 11절) |
| `src/main/java/com/kraft/service/support/OwnershipPolicy.java` | `Post`/`Comment` 공유 소유권 검증 유틸 (record 아님, 정적 클래스, 2026-09-10 추가 — 14절) |
| `src/main/java/com/kraft/domain/comment/CommentRepository.java` | `findAllByPostIdAsc` — JOIN FETCH, id 오름차순 (2026-09-10 추가 — 14절) |
| `src/main/java/com/kraft/service/comment/CommentService.java` | 댓글 save/update/delete/findByPostId (2026-09-10 추가 — 14절) |
| `src/main/java/com/kraft/web/api/CommentApiController.java` | 댓글 REST API 4개 엔드포인트 (2026-09-10 추가 — 14절) |
| `src/main/java/com/kraft/web/dto/comment/*.java` (3종) | `CommentSaveRequestDto`/`CommentUpdateRequestDto`/`CommentResponseDto` (record, 2026-09-10 추가 — 14절) |
| `src/main/resources/templates/user/signup.html` | 회원가입 화면 (2026-09-10 추가 — 14절) |
| `src/test/java/com/kraft/service/comment/CommentServiceTest.java` | `CommentService` Mockito 단위 테스트 9개 (2026-09-10 추가 — 14절) |
| `src/test/java/com/kraft/domain/comment/CommentRepositoryTest.java` | `CommentRepository` `@DataJpaTest` 3개 (2026-09-10 추가 — 14절) |
| `src/test/java/com/kraft/web/api/CommentApiControllerTest.java` | `CommentApiController` `@WebMvcTest` 7개 (2026-09-10 추가 — 14절) |
| `src/main/java/com/kraft/service/post/PostImageService.java` | 게시글 사진 로컬 저장 + URL 생성 (2026-09-10 추가 — 15절) |
| `src/main/java/com/kraft/config/WebConfig.java` | `/images/**` → 업로드 디렉터리 정적 리소스 매핑 (2026-09-10 추가 — 15절) |
| `src/main/java/com/kraft/web/dto/post/ImageUploadResponseDto.java` | 사진 업로드 응답 (record, 2026-09-10 추가 — 15절) |
| `src/main/java/com/kraft/web/dto/user/ChangePasswordRequestDto.java` | 비밀번호 변경 요청 (record, 2026-09-10 추가 — 15절) |
| `src/main/resources/templates/user/change-password.html` | 비밀번호 변경 화면 (2026-09-10 추가 — 15절) |
| `src/test/java/com/kraft/service/post/PostImageServiceTest.java` | `PostImageService` 단위 테스트 5개(`@TempDir` 사용) (2026-09-10 추가 — 15절) |

### 수정된 파일

| 경로 | 주요 변경 |
| --- | --- |
| `build.gradle.kts` | Lombok을 `compileOnly`+`annotationProcessor`(+test 대응)로 변경. **2026-09-09**: `spring-boot-starter-data-jpa-test` 추가(P2-15, 11절), `group`을 `"com"` → `"com.kraft"`로 변경(P2-8, 12절) |
| `domain/user/User.java` | `email` 유니크 제약, 컬럼 길이(`name` 50, `email`/`password` 100), `@Builder` 생성자, `changePassword()`, `promoteToUser()`. **2026-09-09**: 미사용 `@OneToMany posts` 필드 제거(P2-6, 13절) |
| `domain/user/UserRepository.java` | `findByEmail`, `existsByEmail` 추가 |
| `domain/post/Post.java` | `@Getter` 추가, `user` 연관관계 `fetch = LAZY`, `@Builder` 생성자, `update(title, content)`. **2026-09-09**: `picture`에 `@Column(length = 500)` 추가(P2-9, 13절) |
| `domain/post/PostRepository.java` | `findAllDesc()`를 `JOIN FETCH p.user`로 변경(N+1 방지). **2026-09-09**: `List<Post>` → `Page<Post> findAllDesc(Pageable)`(P2-10, 7절) |
| `web/dto/post/*.java` (4종) | **record로 구현** — 3절 참고 |
| `service/post/PostService.java` | `save/update/delete/findById/findAllDesc` 5개 메서드 전체 구현. **2026-09-09**: `update`/`delete`에 `validateOwner()` 권한 검증 추가(P2-4, 6절), `findAllDesc`가 `Pageable`을 받아 `PostsPageResponseDto` 반환하도록 변경(P2-10, 7절). **2026-09-10**: `validateOwner()`가 `OwnershipPolicy`에 1줄 위임하도록 축소(14절) |
| `service/user/UserService.java` | `signUp/changePassword/promoteToUser` 구현. **2026-09-10**: `changePassword` 시그니처를 `(userId, rawPassword)`에서 `(email, currentPassword, newPassword)`로 변경, `passwordEncoder.matches()`로 현재 비밀번호 검증 추가(P3-9, 15절) |
| `web/api/PostApiController.java` | 클래스명 `PostsApiController` → `PostApiController`(파일명 일치), `@Valid`, `Authentication` 기반 작성자 결정. **2026-09-09**: `update`/`delete`가 `Authentication` 전달(P2-4), `findAll`이 `Pageable` 파라미터 지원(P2-10), `GET /api/v1/posts/list` → `GET /api/v1/posts` 경로 변경(P2-11, 13절). **2026-09-10**: `POST /api/v1/posts/images` 사진 업로드 엔드포인트 추가(P3-5, 15절) |
| `web/api/UserApiController.java` | `POST /api/v1/users` 회원가입 엔드포인트. **2026-09-10**: `PUT /api/v1/users/me/password` 비밀번호 변경 엔드포인트 추가(P3-9, 15절) |
| `web/IndexController.java` | `/`, `/posts/save`, `/posts/update/{id}` 라우팅. **2026-09-09**: `/`가 `Pageable`을 받아 페이지네이션 지원(P2-10). **2026-09-10**: `CommentService` 주입, `comments` 모델 추가, `GET /signup` 라우팅 추가(14절), `GET /users/me/password` 라우팅 추가(15절) |
| `application.yml` | DataSource, `ddl-auto`, 세션 스키마 초기화, H2 콘솔, Thymeleaf 캐시 보강. `dialect` 하드코딩 제거. **2026-09-09**: 공통 설정만 남기고 `local`/`prod`로 분리(P2-14, 9절) — 아래 신규 파일 참고. `open-in-view: false` 추가(P2-7, 13절). **2026-09-10**: `app.upload.dir` 추가(P3-5, 15절) |
| `templates/layout/header.html`, `footer.html` | Thymeleaf `th:fragment`로 재구성, CSRF 메타태그 추가. **2026-09-09**: CDN 리소스 3종에 `integrity`/`crossorigin` 추가(P2-13, 10절) |
| `templates/index.html` | Mustache → Thymeleaf(`th:each`, `sec:authorize`, `sec:authentication`), OAuth2 링크 → `/login`. **2026-09-09**: Bootstrap 페이지네이션 UI 추가(P2-10, 7절). **2026-09-10**: 회원가입 링크(14절), 비밀번호 변경 링크(15절) 추가 |
| `templates/post/post-save.html` | Mustache → Thymeleaf, 작성자란을 `sec:authentication="name"` 읽기전용으로 표시. **2026-09-10**: 사진 파일 입력 추가(P3-5, 15절) |
| `templates/post/post-update.html` | Mustache → Thymeleaf, **`label for` 오류 수정**(각 label이 대응 input을 정확히 가리키도록). **2026-09-10**: `xmlns:sec` 추가 + 댓글 목록/작성 폼 추가(14절), 사진 `<img>` 표시 추가(15절) |
| `static/js/app/index.js` | 전역 CSRF 헤더 자동 주입(`ajaxSend`), `save()`에서 `author` 필드 제거. **2026-09-10**: `comment`(save/remove), `signup`(save) 객체 추가(14절), 사진 업로드 2단계 흐름(`uploadImage`→`doSave`), `changePassword`(save) 객체 추가(15절) |
| `domain/comment/Comment.java` | 전면 재작성 — `@GeneratedValue`, `BaseEntity` 상속, `Post`/`User` 연관관계, 테이블명 `comments`로 통일 (2026-09-10 — 14절) |

## 3. DTO를 record로 전환한 결정

구현 중 사용자 요청으로 요청/응답 DTO 5종을 클래스 대신 **Java record**로 작성했다.

```java
public record PostSaveRequestDto(
        @NotBlank(message = "제목은 필수입니다.") String title,
        @NotBlank(message = "내용은 필수입니다.") String content,
        String picture
) {
    public Post toEntity(User user) {
        return Post.builder().title(title).content(content).picture(picture).user(user).build();
    }
}
```

- **대상**: `PostSaveRequestDto`, `PostUpdateRequestDto`, `PostResponseDto`, `PostsListResponseDto`, `SignUpRequestDto`
- **제외**: `Post`, `User` 엔티티는 JPA 요구사항(기본 생성자, 가변성, 프록시 지원)상 record 불가 — 클래스로 유지
- **근거**:
  - Jackson(3.x, `tools.jackson`)이 record를 네이티브로 (역)직렬화 — 별도 모듈 불필요
  - Jakarta Validation 애노테이션(`@NotBlank` 등)은 record 컴포넌트에 붙이면 canonical 생성자 파라미터에 전파되어 `@Valid`가 그대로 동작 (Hibernate Validator 9.1.3으로 실제 검증 완료 — `POST /api/v1/posts`에 빈 제목을 보내면 400)
  - Thymeleaf/SpringEL의 프로퍼티 접근(`${post.title}`)은 Spring Framework 6.1+부터 record accessor(`title()`)를 인식하므로 템플릿 코드 변경이 필요 없었음
- **영향받은 호출부**: `PostService.update()`가 `requestDto.title()`/`requestDto.content()`(getter가 아닌 record accessor)를 사용하도록, `UserApiController.signUp()`이 `requestDto.name()`/`email()`/`password()`를 사용하도록 수정

## 4. 실제 검증 (curl 기반 E2E)

`./gradlew bootRun`으로 앱을 띄운 뒤 세션 쿠키와 CSRF 토큰을 실제로 주고받으며 검증했다.

| 시나리오 | 결과 |
| --- | --- |
| `./gradlew compileJava` / `./gradlew test` | 성공, `contextLoads()` 통과 |
| `GET /` | Mustache 잔재(`{{ }}`) 없이 정상 렌더링 |
| `GET /api/v1/posts/list` (비로그인) | `200`, 빈 배열 — 공개 조회 정책 확인 |
| `POST /api/v1/posts` (CSRF 토큰 없이) | `403` — CSRF 보호 동작 확인 |
| `POST /api/v1/users` (회원가입, CSRF 토큰 포함) | `200`, ID 반환. H2에 `role=GUEST`, 비밀번호 인코딩되어 저장 |
| `POST /login` (폼 로그인, `username=email`) | `302` → `/`, `sec:authentication="name"`으로 "Logged in as: tester@example.com" 표시 |
| `POST /api/v1/posts` (로그인 + CSRF 토큰) | `200`, ID 반환 |
| `GET /api/v1/posts/list` (등록 후) | `[{"id":1,"title":"첫 글","author":"tester","modifiedDate":"..."}]` — `author`가 `User.name`으로 정상 표시(LAZY + `JOIN FETCH`로 N+1/LazyInitializationException 없이 동작) |
| `GET /posts/update/1` | `label for="id"`가 `input id="id"`를 정확히 가리킴(버그 수정 확인), `th:value`로 제목/작성자/내용 정상 바인딩 |
| `PUT /api/v1/posts/1` | `200`, 수정 반영 확인 |
| `DELETE /api/v1/posts/1` | `200`, 목록에서 제거 확인 |
| `GET /h2-console` | `200` 접근 가능. `USERS` 테이블에 `UK_USER_EMAIL` 유니크 제약, `name/email/password` 컬럼 길이 반영 확인 |

## 5. 남은 작업 (P2/P3, 이번 범위 밖)

[`08-issues-and-todo.md`](08-issues-and-todo.md)의 P2/P3 표 그대로 유효하다. 우선순위가 높은 항목만 다시 정리하면:

- ~~컨트롤러 전역 예외 처리(`@RestControllerAdvice`)~~ → **2026-09-09 구현 완료** (8절 참고)
- ~~작성자 본인/`ADMIN`만 수정·삭제 가능하도록 권한 검증~~ → **2026-09-09 구현 완료** (6절 참고)
- ~~페이징 (`Pageable`)~~ → **2026-09-09 구현 완료** (7절 참고)
- ~~`local`/`prod` 프로파일 분리~~ → **2026-09-09 구현 완료** (9절 참고)
- ~~CDN 무결성 속성(`integrity`/`crossorigin`)~~ → **2026-09-09 구현 완료** (10절 참고)
- ~~계층별 테스트 코드~~ → **2026-09-09 구현 완료** (11절 참고, 44개 테스트)
- ~~`group = "com"` → `com.kraft`~~ → **2026-09-09 구현 완료** (12절 참고)
- ~~`open-in-view`/`User.posts`/`Post.picture` 길이/`/api/v1/posts/list` 경로~~ → **2026-09-09 구현 완료** (13절 참고) — **이 시점부터 P2 표 전 항목 해결**
- 댓글(Comment) 엔티티/API/화면 완성
- 이메일 인증 실제 발송, 소셜 로그인

## 6. [추가 구현] 작성자 권한 검증 (2026-09-09, P2-4)

`PostService.update()`/`delete()`가 `Authentication`을 받아 게시글 작성자 본인이거나 `ROLE_ADMIN`
권한을 가진 경우에만 처리하도록 구현했다. 통과하지 못하면 Spring Security의 `AccessDeniedException`을
던지며, `SecurityConfig`가 구성한 필터 체인에 기본 포함된 `ExceptionTranslationFilter`가 이를 가로채
자동으로 `403 Forbidden`을 반환한다 — 별도의 `@RestControllerAdvice` 없이도 인가 실패만큼은
깔끔하게 처리된다.

**검증(curl E2E)**: `userA` 회원가입·로그인 후 글 작성(ID=1) → `userB` 회원가입·로그인 →
`userB`가 `PUT /api/v1/posts/1`, `DELETE /api/v1/posts/1` 시도 → 둘 다 `403`, `GET /api/v1/posts/1`로
원본 데이터(`author: "userA"`)가 그대로임을 확인. 이후 `userA`가 같은 글을 수정·삭제하면 `200`으로
정상 처리됨을 확인했다.

상세 코드와 근거는 [04장](04-architecture-and-layers.md#43-해결됨-postservice-미구현),
[05장](05-api-spec.md#532-게시글-수정---구현검증-완료), [08장 8.5절](08-issues-and-todo.md#85-추가-구현-p2-4-작성자-권한-검증-2026-09-09) 참고.

## 7. [추가 구현] 페이징 (2026-09-09, P2-10)

`PostRepository.findAllDesc()`를 `List<Post>` 반환에서 `Page<Post> findAllDesc(Pageable pageable)`로
바꾸고, `JOIN FETCH p.user` + 별도 `countQuery`를 지정했다. 응답은 `Page`를 직접 직렬화하지 않고
`content`/`page`/`size`/`totalElements`/`totalPages`/`first`/`last`만 담은 `PostsPageResponseDto`
(record)로 감쌌다. `GET /api/v1/posts/list`와 화면(`GET /`) 양쪽에 `@PageableDefault(size = 10)`를
적용했고, `index.html`에 Bootstrap 페이지네이션 UI(이전/페이지 번호/다음)를 추가했다.

**구현 중 발견하고 고친 버그**: `IndexController`가 처음에는 `@RequestParam int page`로 직접 받아
`PageRequest.of(page, size)`를 생성했는데, 1페이지에서 "이전" 링크가 `?page=-1`을 가리키고
(Bootstrap `disabled` 클래스는 시각적 표시일 뿐 클릭을 막지 않음) 이를 실제로 클릭하면
`IllegalArgumentException` → `500`이 발생했다. `PostApiController`처럼 `Pageable`을
`@PageableDefault`로 직접 받도록 통일해, Spring Data의 `PageableHandlerMethodArgumentResolver`가
음수/범위초과 페이지 번호를 자동 보정하도록 해서 해결했다.

**검증(curl E2E)**: 게시글 25건 생성 후 `GET /api/v1/posts/list`(size=10 기본값) → 1페이지는
ID 25~16(`totalPages: 3, first: true, last: false`), 3페이지(`?page=2`)는 ID 5~1(`last: true`)
확인. 화면(`GET /`)도 페이지당 10행, 1/2/3 페이지 링크와 이전/다음 버튼 정상 렌더링 확인.
`?page=-1`, `?page=999` 모두 API·화면에서 `500` 없이 안전하게 처리됨을 재확인했다.

상세 코드와 근거는 [04장](04-architecture-and-layers.md#43-해결됨-postservice-미구현),
[05장 5.3.5절](05-api-spec.md#535-게시글-목록-조회---구현검증-완료-2026-09-09-페이징-추가),
[06장](06-view-and-templates.md), [08장 8.6절](08-issues-and-todo.md#86-추가-구현-p2-10-페이징-2026-09-09) 참고.

## 8. [추가 구현] 전역 예외 처리기 (2026-09-09, P1-12)

`com.kraft.web.exception.ApiExceptionHandler`(`@RestControllerAdvice(basePackages = "com.kraft.web.api")`)를
신설했다. Spring Boot 4의 `ProblemDetail`(RFC 9457)을 그대로 반환해 커스텀 오류 클래스나 새
의존성 없이 상태 코드·`Content-Type: application/problem+json`이 자동 설정되도록 했다.

처리 대상: `IllegalArgumentException`(400, "찾을 수 없음"/"중복" 등 서비스 계층 검증 실패),
`MethodArgumentNotValidException`(400, `@Valid` 필드별 오류), `HttpMessageNotReadableException`
(400, 잘못된 JSON), `AccessDeniedException`(403, P2-4 권한 검증과 연동), 그 외 모든 예외를 잡는
catch-all `Exception`(500, 클라이언트에는 원인 비노출·서버 로그에만 기록).

**범위를 `com.kraft.web.api`로 한정**했다 — 전역으로 두면 화면 컨트롤러(`IndexController`)의
Thymeleaf 렌더링 오류까지 JSON으로 바뀌는 문제가 있어, `GET /posts/update/999`가 여전히
`500`(Spring Boot 기본, 변경 없음)을 반환함을 curl로 재확인했다. `AccessDeniedException`을
명시적으로 처리하지 않으면 catch-all에 걸려 `403`이 `500`으로 바뀌는 회귀가 생길 뻔했다는
점도 구현 중 확인했다.

**검증(curl E2E, 로그인·CSRF 토큰 포함)**: 존재하지 않는 게시글 조회, 잘못된 JSON, 빈 제목
등록, 이메일 중복 가입, 타인 글 삭제 시도 5가지 모두 이전에는 `500`(또는 형식이 다른 `403`)
이었던 것이 이제 일관된 `ProblemDetail` JSON으로 응답함을 확인했다. 정상 흐름(목록 조회,
화면 렌더링)에는 회귀가 없음을 재확인했다.

상세 코드, 설계 결정, 전/후 비교표는 [04장 4.6절](04-architecture-and-layers.md#46-예외-처리-전략---해결2026-09-09-추가-구현),
[05장 5.4절](05-api-spec.md#54-예외-응답---해결됨-2026-09-09-apiexceptionhandler),
[08장 8.7절](08-issues-and-todo.md#87-추가-구현-p1-12-전역-예외-처리기-2026-09-09) 참고.

## 9. [추가 구현] 프로파일 분리 (2026-09-09, P2-14)

단일 `application.yml`을 공통 파일(`application.yml`) + `application-local.yml` +
`application-prod.yml`로 분리했다. `spring.profiles.default: local`을 공통 파일에 지정해,
프로파일을 명시하지 않으면 자동으로 `local`이 적용되도록 해 기존 `./gradlew bootRun`/
`./gradlew test` 사용법을 그대로 유지했다(회귀 없음).

`local`과 `prod`의 차이: DataSource(H2 하드코딩 vs `${DB_URL}` 등 환경변수), `ddl-auto`
(`create-drop` vs `validate`), H2 콘솔(활성화 vs **비활성화**), 세션 스키마 초기화(`always` vs
`never`), Thymeleaf 캐시(`false` vs `true`). 운영 프로파일에서 H2 콘솔을 완전히 꺼서,
`SecurityConfig`가 열어둔 `/h2-console/**` permitAll·CSRF 예외가 가리킬 대상 자체를 없앴다
(코드 분기 없이 설정만으로 잠재적 보안 구멍을 해소).

**검증(3가지 실제 시나리오, 운영 MariaDB가 없는 환경이라 datasource 연결만 H2로 대체)**:

1. 무프로파일 기동 → 로그에 `"falling back to 1 default profile: 'local'"`, 기존 동작과
   동일함을 재확인(회귀 없음).
2. `prod` + 환경변수 없음 → `"The following 1 profile is active: 'prod'"` 확인 후
   `${DB_URL}`이 해석되지 않아 드라이버가 거부(`Driver org.mariadb.jdbc.Driver claims to
   not accept jdbcUrl, ${DB_URL}`)하며 기동 자체가 실패 — 운영 자격 증명 없이는 절대 뜨지
   않는 fail-fast 동작을 확인.
3. `prod` + H2로 datasource만 임시 대체 → 빈 스키마에 대해
   `Schema validation: missing table [comment]`로 실패(`ddl-auto: validate`가 실제
   적용되어 `create-drop`이 아님을 증명) + `"H2 console available"` 로그가 전혀 나타나지
   않음(`h2.console.enabled: false` 적용 증명).

실제 MariaDB로의 최종 접속 자체는 이 환경에 운영 DB가 없어 검증하지 못했다 — 그 외 `prod`
전용 설정(스키마 검증 방식, 콘솔 비활성화)은 모두 확인했다.

상세 코드와 근거는 [07장 7.4절](07-configuration.md#74-프로파일-분리---해결됨-2026-09-09-추가-구현),
[08장 8.8절](08-issues-and-todo.md#88-추가-구현-p2-14-프로파일-분리-2026-09-09) 참고.

## 10. [추가 구현] CDN 무결성 속성 (2026-09-09, P2-13)

`layout/header.html`(Bootstrap CSS)과 `layout/footer.html`(jQuery, Bootstrap JS) 3개 CDN
태그에 `integrity`(SRI)와 `crossorigin="anonymous"`를 추가했다.

**해시는 기억이나 문서값을 베끼지 않고 실측했다.** 템플릿이 실제로 참조하는 URL에서 파일을
직접 내려받아 `openssl dgst -sha384`로 계산했다 — SRI 해시가 실제 파일과 다르면 브라우저가
리소스 로딩을 통째로 차단해 CSS/jQuery가 사라지는 회귀로 이어질 수 있기 때문이다. Bootstrap
CSS/JS 두 값은 공식 문서 게시값과 실측치가 일치했지만, jQuery 값은 기억에 의존했다면 다른
계열(sha256) 값을 잘못 넣었을 뻔했다 — 실측 검증이 실제로 필요했던 사례다.

| 파일 | SRI 해시(sha384) |
| --- | --- |
| `jquery-3.3.1.min.js` | `sha384-tsQFqpEReu7ZLhBV2VZlAu7zcOV+rXbYlF2cqB8txI/8aZajjp4Bqd+V6D5IgvKT` |
| `bootstrap.min.js` (4.3.1) | `sha384-JjSmVgyd0p3pXB1rRibZUAYoIIy6OrQ6VrjIEaFf/nJGzIxFDsf4x0xIM+B07jRM` |
| `bootstrap.min.css` (4.3.1) | `sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T` |

**검증**: `GET /`, `GET /posts/save` 응답 HTML에 3개 태그 모두 `integrity`/`crossorigin`이
정확히 렌더링됨을 확인했다. curl은 SRI를 강제하지 않으므로(브라우저 전용 기능) 렌더링된
해시값이 위 표의 실측치와 정확히 일치하는지 diff로 대조하는 방식으로 검증을 대체했다.
`./gradlew clean compileJava test`도 회귀 없이 통과했다.

상세 코드와 계산 과정은 [06장 6.5절](06-view-and-templates.md#65-정적-리소스),
[08장 8.9절](08-issues-and-todo.md#89-추가-구현-p2-13-cdn-무결성-속성-2026-09-09) 참고.

## 11. [추가 구현] 계층별 테스트 코드 (2026-09-09, P2-15)

의존성에 이미 있던 테스트 스타터(`webmvc-test`, `security-test`, `thymeleaf-test`,
`validation-test`)를 실제로 활용해 서비스(Mockito)/리포지토리(`@DataJpaTest`)/웹(`@WebMvcTest`)
3계층에 걸쳐 **테스트 44개**를 작성했다. `spring-boot-starter-data-jpa-test`는 미선언 상태였는데,
`./gradlew dependencies`로 Boot 4.1.1에서 실제 해석되는지 먼저 확인한 뒤 추가했다.

| 파일 | 유형 | 개수 |
| --- | --- | --- |
| `PostServiceTest` | Mockito 단위 테스트 | 10 |
| `UserServiceTest` | Mockito 단위 테스트 | 5 |
| `PostRepositoryTest` | `@DataJpaTest` | 4 |
| `UserRepositoryTest` | `@DataJpaTest` | 5 |
| `PostApiControllerTest` | `@WebMvcTest` + 실제 `SecurityConfig` | 8 |
| `UserApiControllerTest` | `@WebMvcTest` + 실제 `SecurityConfig` | 5 |
| `IndexControllerTest` | `@WebMvcTest` + 실제 `SecurityConfig` | 6 |

**작성 중 실제로 부딪힌 문제 3가지** (전부 애플리케이션 버그가 아니라 테스트 작성 시 처음
알게 된 프레임워크 사실):

1. Boot 4.1.1은 `@WebMvcTest`/`@DataJpaTest`/`TestEntityManager`의 패키지를 Boot 3 관례와
   다르게 재배치했다(`org.springframework.boot.webmvc.test.autoconfigure` 등). 실제 JAR을
   열어 정확한 위치를 확인해야 했다.
2. `@DataJpaTest`는 `@EnableJpaAuditing`이 선언된 `JpaConfig`를 자동으로 스캔하지 않는다 —
   `@Import(JpaConfig.class)`를 명시해야 감사 필드 검증이 의미 있어진다.
3. MockMvc는 처리되지 않은 예외를 500 응답으로 바꿔주지 않고 그대로 던진다(실제 Tomcat과
   다른 동작) — `IndexControllerTest`의 관련 테스트를 `assertThatThrownBy(...)`로 수정했다.

**결과**: `./gradlew clean test` 기준 **44개 테스트 전부 통과**. 특히 `UserRepositoryTest`가
`email` 유니크 제약을 실제 `DataIntegrityViolationException`으로, `IndexControllerTest`가
과거 실제로 `500`을 유발했던 `page=-1` 버그(P2-10, 8.6절)의 회귀를 자동으로 검증하게 되어,
이후에는 `bootRun`+curl 수동 재현 없이 `./gradlew test` 한 번으로 확인할 수 있다.

상세 구성과 설계 결정은 [07장 7.6절](07-configuration.md#76-테스트-실행---통과-2026-09-09부터-계층별-테스트-44개로-확장),
[08장 8.10절](08-issues-and-todo.md#810-추가-구현-p2-15-계층별-테스트-코드-2026-09-09) 참고.

## 12. [추가 구현] group 좌표 정정 (2026-09-09, P2-8)

`build.gradle.kts`의 `group = "com"`을 `group = "com.kraft"`로 바꿔 패키지 구조(이미
`com.kraft`)와 일치시켰다. `group`은 Maven/Gradle 퍼블리싱 좌표에만 영향을 주고 JAR 파일명
(`rootProject.name`로 결정)이나 패키지 구조와는 무관하다는 것을 확인한 뒤 진행했다 —
순수 메타데이터 변경, 런타임 동작 영향 없음.

**검증**: `./gradlew properties`로 `group: com.kraft` 확인, `./gradlew clean build`로 JAR이
정상 생성됨(매니페스트의 `Start-Class: com.kraft.KraftApplication` 그대로)을 확인,
`./gradlew clean test`로 44개 테스트 전부 회귀 없이 통과함을 확인했다.

상세는 [02장 2.1절](02-build-and-dependencies.md#21-buildgradlekts-전문-분석),
[08장 8.11절](08-issues-and-todo.md#811-추가-구현-p2-8-group-좌표-정정-2026-09-09) 참고.

## 13. [추가 구현] P2 나머지 4개 항목 일괄 정리 (2026-09-09) — 이 시점부터 P2 표 전 항목 해결

작업 전 사용처를 grep으로 먼저 확인해 안전함을 검증했다: `User.posts`는 프로젝트 어디서도
참조되지 않았고, `/api/v1/posts/list`는 `index.js`가 호출하지 않으며(`IndexController`가
서버사이드에서 `PostService`를 직접 호출) 오직 외부 API 소비자만 이 경로를 쓴다.

| 항목 | 변경 |
| --- | --- |
| P2-7 `open-in-view` | 공통 `application.yml`에 `spring.jpa.open-in-view: false` 추가 |
| P2-6 `User.posts` | 미사용 `@OneToMany posts` 필드와 관련 import 제거 |
| P2-9 `Post.picture` 길이 | `@Column(length = 500)` 추가 |
| P2-11 목록 경로 | `@GetMapping("/api/v1/posts/list")` → `@GetMapping("/api/v1/posts")` |

**검증 중 실제로 발견한 부작용**: 경로 변경 후 옛 경로 `/api/v1/posts/list`를 호출하면
`GET /api/v1/posts/{id}`의 `{id}`에 문자열 `"list"`가 매칭되어 `Long` 타입 변환에 실패하는데,
이 예외(`MethodArgumentTypeMismatchException`)를 당시 `ApiExceptionHandler`가 처리하지
않고 있어 catch-all에 잡혀 **500**이 되는 것을 curl로 직접 확인했다. 클라이언트 입력 오류이므로
500보다 400이 맞다고 판단해, `ApiExceptionHandler`에 전용 핸들러를 추가로 구현했다(400 +
`"요청 값의 형식이 올바르지 않습니다: id"`). 회귀 테스트(`PostApiControllerTest`)도 추가했다.

**검증(curl E2E + 자동 테스트)**:

- `bootRun` 로그에서 `open-in-view is enabled by default` 경고가 사라짐을 확인, `GET /`이
  여전히 `200`으로 정상 렌더링되어 `LazyInitializationException`이 없음을 확인
- `picture` 필드가 포함된 글 등록 → 정상 저장 확인
- `GET /api/v1/posts`(새 경로) → `200`, `GET /api/v1/posts/list`(옛 경로) → `400`(수정 후)
- `./gradlew clean test` → **45개 테스트 전부 통과**(기존 44개 + 타입 불일치 회귀 테스트 1개)

상세는 [03장](03-domain-model.md), [04장 4.6절](04-architecture-and-layers.md#46-예외-처리-전략---해결2026-09-09-추가-구현),
[05장 5.3.5절](05-api-spec.md#535-게시글-목록-조회---구현검증-완료-2026-09-09-페이징-추가),
[07장](07-configuration.md)의 7.2.6절,
[08장 8.12절](08-issues-and-todo.md#812-추가-구현-p2-1p2-6p2-7p2-9p2-11-나머지-정리-2026-09-09) 참고.

## 14. [추가 구현] 댓글(Comment) 기능 + 회원가입 화면 (2026-09-10, P3-1~3·P3-7)

사용자가 P3 항목 중 두 가지(댓글 기능, 회원가입 화면)를 선택해 구현했다. 새 의존성은 추가하지
않았고, 기존 `Post`/`PostService`/`PostApiController`/`SecurityConfig`/`index.js`/
`ApiExceptionHandler`의 패턴을 그대로 재사용했다. 상세 계획은 Plan 모드에서 별도 서브에이전트로
설계를 검증받은 뒤 진행했다(`C:\Users\portu\.claude\plans\lazy-rolling-hennessy.md`).

**구현 전 사전 검증**(구현에 들어가기 전 위험을 먼저 확인):
- `PostServiceTest`가 `AccessDeniedException`을 `.isInstanceOf(...)`로만 검증(메시지 미검증)하고
  `PostApiControllerTest`가 `PostService`를 완전히 모킹함을 `grep`으로 확인 → `OwnershipPolicy`
  추출이 기존 45개 테스트에 영향 없음을 리팩터링 전에 확보.
- `SecurityConfig`의 기존 매처(`GET /api/v1/posts/**` permitAll, `/api/v1/**` authenticated,
  `anyRequest().permitAll()`)가 신규 경로(`/api/v1/posts/{postId}/comments`,
  `/api/v1/comments/{id}`, `/signup`)를 매처 단위로 정확히 커버함을 표로 검증 → **`SecurityConfig.java`는
  한 줄도 수정하지 않았다.**

**핵심 설계 결정**: `PostService.validateOwner()`와 완전히 동일한 정책이 `CommentService`에도
필요해, 복제 대신 `service/support/OwnershipPolicy`(순수 정적 유틸)로 추출했다. `User.java`
클래스 주석이 "일반사용자는 Post, Comment의 작성, 수정, 삭제가 가능하다"고 두 도메인을 나란히
명시하고 있어 우연이 아닌 진짜 공유 정책이라고 판단했다. `PostService.validateOwner()`는 1줄
위임으로 축소했다(메시지 문자열 동일 유지 → 회귀 없음).

댓글 "수정" 기능은 API(`PUT /api/v1/comments/{id}`)만 제공하고 화면 UI에는 넣지 않았다 —
인라인 편집은 클라이언트 상태 관리가 늘어나는 데 비해 댓글은 삭제 후 재작성으로 대체 가능한
짧은 텍스트라 판단했다(상세 근거는 04장 4.8절).

**검증(curl E2E, 13단계 전부 성공)**: 회원가입 화면 렌더링 → 가입 → 로그인 → 게시글 작성 →
댓글 작성(공개 목록 조회로 비로그인도 확인) → 다른 사용자가 삭제 시도 시 `403`(원본 데이터 불변
확인) → 작성자 본인 삭제 시 `200` → 게시글 페이지에 댓글이 정확히 반영 → 비로그인 시 댓글
작성 폼/삭제 버튼이 전혀 렌더링되지 않고 로그인 유도 링크만 노출됨을 확인했다. 상세 curl
로그는 [08장 8.13절](08-issues-and-todo.md#813-추가-구현-p3-댓글comment-기능--회원가입-화면-2026-09-10) 참고.

`./gradlew clean test` → 기존 45개 + 신규 20개(`CommentServiceTest` 9, `CommentRepositoryTest` 3,
`CommentApiControllerTest` 7, `IndexControllerTest` +1) = **65개 테스트 전부 통과**.

### 남은 P3 항목 (이번 범위에서 명시적으로 제외)

- 실제 이메일 인증 토큰 발급/메일 발송(`spring-boot-starter-mail` 필요) — **유일하게 남은 P3**
- ~~소셜 로그인~~ → 사용자가 명시적으로 제외 결정(2026-09-10)
- ~~게시글 사진 업로드 실제 연동~~ → **2026-09-10 추가 구현** (P3-5, 15절)
- ~~비밀번호 변경 HTML 화면~~ → **2026-09-10 추가 구현** (P3-9, 15절)
- 댓글 페이징/대댓글, 댓글 인라인 수정 UI (범위 밖으로 유지)

## 15. [추가 구현] 게시글 사진 업로드 + 비밀번호 변경 화면 (2026-09-10, P3-5·P3-9)

사용자가 남은 P3 중 이 두 항목만 선택했고, 소셜 로그인은 "OAuth 인증방식은 제거한다"고 명시적으로
제외를 결정했다(코드상 애초에 도입된 적이 없어 실제로 제거할 것은 없었음). 실제 이메일 인증
발송은 이번에도 선택되지 않아 유일하게 남은 P3 항목이 되었다.

**게시글 사진 업로드**: 새 의존성 없이 `spring-boot-starter-webmvc`의 Multipart 지원만으로
구현했다. `PostImageService`가 확장자 화이트리스트(jpg/jpeg/png/gif/webp)·5MB 크기 제한을
검증해 `app.upload.dir`(기본 `uploads/images`)에 UUID 파일명으로 저장하고, `WebConfig`가
`/images/**`(기존에 이미 `SecurityConfig`에 permitAll로 있던 경로를 재사용 — **보안 설정
변경 없음**)로 서빙한다. `POST /api/v1/posts/images`(인증 필요, 역시 `SecurityConfig` 변경
없이 기존 `/api/v1/**` authenticated 규칙에 자동으로 걸림)가 업로드를 받아 URL을 반환하면,
`index.js`가 그 URL을 `PostSaveRequestDto.picture`에 담아 게시글을 등록하는 2단계 흐름으로
구현했다.

**비밀번호 변경**: `UserService.changePassword()`의 시그니처를 바꿔 **현재 비밀번호 검증을
실제로 추가**했다(기존에는 `userId`+새 비밀번호만 받아 누구든 검증 없이 바꿀 수 있는 상태였으나
호출하는 곳이 없어 실제 위험은 없었다 — 화면을 붙이는 이번 시점에 `User.java` 클래스 주석이
원래 의도했던 안전장치를 구현). `PUT /api/v1/users/me/password`도 `SecurityConfig`의
`/api/v1/users`(정확히 일치하는 경로만 매치, 와일드카드 없음) permitAll 규칙에는 걸리지 않고
`/api/v1/**` authenticated로 자동 커버됨을 사전에 확인한 뒤 진행했다 — **이번에도
`SecurityConfig.java`는 한 줄도 수정하지 않았다.**

**검증(curl E2E)**: 이미지 업로드(CSRF 없음 403, 미인증 302, 정상 업로드 200+URL, 반환 URL
직접 접근 시 원본과 동일한 바이트 크기, 허용 안 된 확장자 400) → 업로드 URL로 게시글 등록 →
단건 조회 응답과 수정 화면(`<img>` 태그)에 정확히 반영 확인. 비밀번호 변경(화면 렌더링, 미인증
302, 현재 비밀번호 오입력 400, 정상 변경 204) → **옛 비밀번호 로그인 실패, 새 비밀번호 로그인
성공**까지 실제로 재로그인해 검증했다(상태 코드만이 아니라 실제 동작 확인).

Windows 환경 특이사항: `curl.exe`(mingw32 네이티브 빌드)가 Git Bash의 MSYS 경로(`/tmp/...`)를
`-F` 멀티파트 옵션 안에서는 자동 변환하지 못해 최초 시도가 실패했다(`HTTP 000`) — `cygpath -w`로
Windows 경로로 바꿔 재시도해 해결했다. 애플리케이션 자체의 문제는 아니었다.

`./gradlew clean test` → 기존 65개 + 신규 16개(`PostImageServiceTest` 5, `UserServiceTest`
+2, `PostApiControllerTest` +4, `UserApiControllerTest` +4, `IndexControllerTest` +1) =
**81개 테스트 전부 통과**.

상세는 [03장](03-domain-model.md), [05장 5.3.7~5.3.8절](05-api-spec.md),
[06장 6.4.4절](06-view-and-templates.md#644-사진-업로드--비밀번호-변경-화면---신규-구현-2026-09-10-p3),
[08장 8.14절](08-issues-and-todo.md#814-추가-구현-p3-5-게시글-사진-업로드--p3-9-비밀번호-변경-화면-2026-09-10) 참고.

## 16. [추가 구현] 이메일 인증 플로우 (2026-09-10, P3-4) — 이 시점부터 P3 표 전 항목 해결

사용자가 소셜 로그인을 재차 명시적으로 제외("Oauth 인증방식은 제거한다")한 뒤, P3의 마지막
남은 항목인 실제 이메일 인증 발송을 진행했다. `spring-boot-starter-mail`이 필요함을
AskUserQuestion으로 사전에 알리고 승인받아 추가했다 — **이번 세션 전체에서 새 의존성을
추가한 유일한 사례**다.

**설계**: `EmailSender` 인터페이스를 프로파일별로 분리했다 — `ConsoleEmailSender`
(`@Profile("local")`, `JavaMailSender` 의존성 없이 콘솔 로그만 남김)와 `SmtpEmailSender`
(`@Profile("prod")`, 실제 SMTP 발송). `local`은 `spring.mail.host`를 설정하지 않으므로 Boot가
`JavaMailSender` 빈 자체를 만들지 않는다(jar 역컴파일로 조건 확인) — 로컬 개발에 SMTP 서버가
전혀 필요 없다.

**신규 도메인**: `EmailVerificationToken`(unique token, `User` 연관관계, `expiresAt`,
`isExpired()`) + `EmailVerificationTokenRepository`. **신규 서비스**: `EmailVerificationService`
— `sendVerificationEmail`(UUID 토큰 발급+저장+메일 발송), `sendVerificationEmailSafely`
(회원가입 흐름에서 호출, 발송 실패를 흡수해 가입 자체는 실패시키지 않음), `verify`(토큰 검증 →
`UserService.promoteToUser()`로 승격 → 토큰 삭제, 1회용).

`UserApiController.signUp()`이 가입 성공 직후 `sendVerificationEmailSafely`를 호출하도록
수정했고, `IndexController`에 `GET /users/verify?token=...` 화면 라우트를 추가했다(JSON API가
아닌 이메일 링크 클릭용). 이번에도 **`SecurityConfig.java`는 전혀 수정하지 않았다** — 이
경로는 `anyRequest().permitAll()` catch-all에 걸려 자동으로 공개된다(`/posts/save`, `/signup`
과 동일한 패턴).

**의도적으로 범위 밖에 남긴 것**: `GUEST`의 쓰기 권한을 실제로 차단하는 인가 규칙 차등화는
구현하지 않았다. 이를 강제하려면 `SecurityConfig`의 인가 규칙을 이번 세션 최초로 수정해야 하고,
사용자가 명시적으로 요청하지 않았기 때문이다.

**검증(curl E2E, `bootRun` + `local` 프로파일)**: 회원가입 → 콘솔 로그에 인증 링크
(`http://localhost:8080/users/verify?token=<uuid>`) 출력 확인 → 해당 토큰으로 검증 요청 →
`200`+성공 메시지, Hibernate 로그에 `update users set ... role=?` 실행 확인(실제 DB 반영) →
**같은 토큰 재요청 시 "유효하지 않은 인증 링크입니다"**(1회용 확인) → 존재하지 않는 토큰도
동일하게 실패 처리됨을 확인.

`./gradlew clean test` → 기존 81개 + 신규 10개(`EmailVerificationServiceTest` 7,
`EmailVerificationTokenRepositoryTest` 2, `IndexControllerTest` +1) = **91개 테스트 전부 통과**.

상세는 [03장 3.9절](03-domain-model.md#39-emailverificationtoken---구현-완료-2026-09-10),
[04장 4.9절](04-architecture-and-layers.md#49-이메일-인증-계층---구현-완료-2026-09-10),
[05장 5.3.9절](05-api-spec.md), [07장 7.8절](07-configuration.md#78-이메일-발송-설정-springmail---구현-완료-2026-09-10-p3-4),
[08장 8.15절](08-issues-and-todo.md#815-추가-구현-p3-4-이메일-인증-플로우-2026-09-10) 참고.

**이로써 P3의 모든 항목이 해결되었다**(소셜 로그인은 사용자 결정으로 명시적 제외) — 이 프로젝트는
P0/P1/P2/P3 전 범위가 구현·검증 완료된 상태다.

## 17. [전체 기능 검증] 브라우저 + curl E2E 전수 검증 (2026-09-10)

P0~P3 구현이 모두 완료된 상태에서, 그동안 주로 curl로만 검증해온 기능들을 실제 Chrome 브라우저
(Claude in Chrome 확장)로 직접 구동해 회귀 여부를 재확인했다. 새 기능 구현이나 버그 수정은
없는 순수 검증 작업이다.

**브라우저로 확인**: 회원가입 → 콘솔 로그의 인증 링크 클릭으로 이메일 인증 → 로그인 → 게시글
등록(실제 파일 업로드 포함, DOM에서 `<img src="/images/...">` 직접 확인) → 게시글 수정 → 댓글
등록 → 비밀번호 변경 → 로그아웃 → 옛 비밀번호 로그인 거부(`Invalid credentials`) → 새 비밀번호
로그인 성공까지 전부 정상 동작을 확인했다.

**브라우저 자동화의 한계(도구 제약, 앱 결함 아님)**: `index.js`가 저장/삭제 피드백에 쓰는 네이티브
`alert()`/`confirm()`은 CDP 기반 자동화가 입력을 전달할 수 없다 — 특히 `confirm()`은 페이지
이동으로 강제 해제하면 항상 "취소"로 처리되어, 삭제(댓글·게시글) 흐름만은 브라우저 대신 curl로
대체 검증했다.

**curl로 보완 검증**: 타인이 댓글 삭제 시도 → `403`(소유권 검증), 작성자 본인 댓글 삭제 → `200`
+ 목록 재조회 시 빈 배열, 작성자 본인 게시글 삭제 → `200` + 재조회 시 `400`.

결론: 전 기능 회귀 없음. 상세는
[08장 8.16절](08-issues-and-todo.md#816-전체-기능-검증-브라우저--curl-e2e-전수-검증-2026-09-10)
참고.

## 18. [추가 구현] 네비게이션 정책 + 회원가입 검증 강화 + 이메일 암호화 (2026-09-10)

사용자가 연속으로 네 가지를 요청했다: (1) "글 등록" 버튼은 로그인 시에만 노출, (2) `/posts/save`
에서 로그아웃 시 항상 메인으로 이동, (3) 회원가입 시 이름·이메일 DB 중복확인 + 비밀번호
복잡도(대/소문자·특수문자) + 비밀번호 2회 입력 확인, (4) 비밀번호·이메일 DB 암호화 저장.

비밀번호는 이미 BCrypt로 암호화되어 있었다. 이메일은 처음 SHA-512(단방향 해시)가 제안됐으나,
인증 메일 재발송·화면 표시에 원문이 필요해 해시만으로는 불가능함을 설명하고 AskUserQuestion으로
확인한 뒤 **AES로 암호화한 `email` 컬럼 + SHA-512 해시를 담은 `email_hash` 컬럼(조회·중복확인·
유니크 제약 전용) 분리** 방식을 사용자가 직접 선택했다. 새 의존성 없음
(`spring-security-crypto`는 `spring-boot-starter-security`의 기존 전이 의존성).

`UserRepository.findByEmail`/`existsByEmail`을 쓰던 6개 지점(`UserDetailsServiceImpl`,
`CommentService`, `PostService`, `EmailVerificationService`, `UserService` 2곳)을 전부
해시 기반 조회로 바꿨다. `User` 자바 객체의 `email` 필드는 항상 평문이므로(컨버터는 JDBC 바인딩
경계에서만 작동) 나머지 코드는 손댈 필요가 없었다.

`./gradlew test` → 기존 91개 + 신규 4개 = **95개 전부 통과**. `bootRun` + H2 콘솔로 `email`
컬럼이 실제 hex 암호문으로, `email_hash`가 128자 SHA-512로 저장됨을 직접 확인했고, 로그인·회원가입
검증·네비게이션 바 노출·로그아웃 리다이렉트까지 브라우저로 재검증했다. 상세는
[04장 4.10절](04-architecture-and-layers.md#410-회원가입-검증-강화--이메일-암호화---신규-구현-2026-09-10),
[06장](06-view-and-templates.md#네비게이션-바-정책-조정--로그아웃-목적지-예외-2026-09-10),
[07장 7.9절](07-configuration.md#79-이메일-암호화-키-설정-appsecurityemail-encryption-key---구현-완료-2026-09-10),
[08장 8.17절](08-issues-and-todo.md#817-추가-구현-로그인-후-네비게이션-정책--회원가입-검증-강화--이메일-암호화-2026-09-10) 참고.
