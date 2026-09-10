# 08. 이슈와 TODO

> **2026-09-09 업데이트**: P0(빌드 복구)와 P1(실사용 가능한 상태)이 모두 구현·검증 완료되었다.
> 이후 사용자 요청으로 P2 항목 중 작성자 권한 검증(P2-4, 8.5절), 페이징(P2-10, 8.6절),
> 전역 예외 처리기(P1-12, 8.7절), 프로파일 분리(P2-14, 8.8절), CDN 무결성 속성(P2-13, 8.9절),
> 계층별 테스트 코드(P2-15, 8.10절), `group` 좌표 정정(P2-8, 8.11절), `open-in-view`/
> `User.posts`/`Post.picture` 길이/`/api/v1/posts/list` 경로 정리(P2-7, P2-6, P2-9, P2-11,
> 8.12절)를 추가로 구현·검증했다. 이 시점부터 **P2 표의 모든 항목이 해결됨**. **2026-09-10**:
> 사용자가 선택한 P3 항목 — 댓글(Comment) 기능 전체 스택(P3-1~3)과 회원가입 HTML 화면(P3-7) —
> 도 구현·검증했다(8.13절). 같은 날 게시글 사진 업로드(P3-5)와 비밀번호 변경 화면(P3-9)도
> 추가로 구현했고(8.14절), 소셜 로그인은 사용자가 명시적으로 제외를 결정했다(P3-8). 나머지
> P3(실제 이메일 인증 발송)는 여전히 미해결.
> 8.1~8.2절은 원래 발견 당시 기록을 유지하고 해결 여부를 표시했다. 8.3절의 작업 순서는
> 실제로 그대로 수행되었다. 나머지 P2/P3는 여전히 미해결이며 이후 별도 작업이다.
> 전체 검증 결과는 [09. 구현 요약](09-implementation-summary.md) 참고.

## 8.1 컴파일 오류 원문 (해결됨)

`./gradlew compileJava` 실행 결과 **10건**의 오류가 발생했다(현재는 0건, ✅ 해결).

```
1) PostApiController.java:15
   error: class PostsApiController is public, should be declared in a file named PostsApiController.java

2) User.java:48
   error: cannot find symbol   symbol: method getKey()   location: variable role of type Role

3-5) Role.java:10,11,12
   error: constructor Role in enum Role cannot be applied to given types;
   required: no arguments / found: String,String

6) PostApiController.java:21   error: cannot find symbol   symbol: method save(PostSaveRequestDto)
7) PostApiController.java:26   error: cannot find symbol   symbol: method update(Long,PostUpdateRequestDto)
8) PostApiController.java:31   error: cannot find symbol   symbol: method delete(Long)
9) PostApiController.java:37   error: cannot find symbol   symbol: method findById(Long)
10) PostApiController.java:42  error: cannot find symbol   symbol: method findAllDesc()
```

### 원인 3가지 (모두 해결)

| # | 근본 원인 | 유발한 오류 | 상태 |
| --- | --- | --- | --- |
| A | **Lombok이 `annotationProcessor`로 등록되지 않음** | 2, 3, 4, 5 | ✅ `compileOnly`+`annotationProcessor`로 수정 |
| B | **파일명과 public 클래스명 불일치** | 1 | ✅ 클래스명을 `PostApiController`로 통일 |
| C | **`PostService`에 메서드 미구현** | 6, 7, 8, 9, 10 | ✅ 5개 메서드 전체 구현 |

`./gradlew compileJava`, `./gradlew test`가 모두 통과함을 확인했다.

## 8.2 심각도별 이슈 목록 (해결 현황 반영)

### P0 — 빌드 차단 (즉시 조치) — ✅ 전부 해결

| ID | 항목 | 위치 | 조치 | 상태 |
| --- | --- | --- | --- | --- |
| P0-1 | Lombok 애노테이션 프로세서 미등록 | `build.gradle.kts` | `compileOnly` + `annotationProcessor`로 변경 | ✅ |
| P0-2 | 파일명/클래스명 불일치 | `web/api/PostApiController.java` | 클래스명을 `PostApiController`로 변경 | ✅ |
| P0-3 | `PostService` 메서드 5개 미구현 | `service/post/PostService.java` | `save`/`update`/`delete`/`findById`/`findAllDesc` 구현 | ✅ |
| P0-4 | DTO 4종이 빈 클래스 | `web/dto/post/*` | 필드 + 검증 애노테이션 + 변환 메서드 추가 | ✅ (record로 구현, [04장 4.4절](04-architecture-and-layers.md) 참고) |
| P0-5 | `Post`에 `@Getter` 없음 | `domain/post/Post.java` | `@Getter` 추가 | ✅ |
| P0-6 | `Post`/`User` 생성자 부재 | `domain/**` | `@Builder` 생성자 추가 | ✅ |

### P1 — 런타임 오동작 — 11/12 해결

| ID | 항목 | 영향 | 조치 | 상태 |
| --- | --- | --- | --- | --- |
| P1-1 | 템플릿이 Mustache 문법인데 Thymeleaf만 존재 | 화면이 전혀 렌더링되지 않음 | 템플릿 5개 Thymeleaf 전환 ([06장](06-view-and-templates.md)) | ✅ |
| P1-2 | `@EnableJpaAuditing` 누락 | `createdAt`/`updatedAt`이 항상 `null` | `JpaConfig` 추가 ([03장 3.2절](03-domain-model.md)) | ✅ |
| P1-3 | `SecurityConfig` 부재 | 목록 조회조차 로그인 요구, AJAX 403 | `SecurityFilterChain` 정의 ([07장 7.3절](07-configuration.md)) | ✅ |
| P1-4 | AJAX가 CSRF 토큰 미전송 | POST/PUT/DELETE 전부 403 | 헤더 주입 ([05장 5.5절](05-api-spec.md)) | ✅ |
| P1-5 | `session.jdbc.initialize-schema` 미설정 | MariaDB 전환 시 세션 테이블 부재로 실패 | `initialize-schema: always` | ✅ |
| P1-6 | `ddl-auto` 미설정 | 테이블 미생성 | `create-drop`으로 명시 | ✅ |
| P1-7 | `dialect: H2Dialect` 하드코딩 | MariaDB 접속 시 잘못된 SQL | 설정 제거 (Hibernate 자동 판별) | ✅ |
| P1-8 | `IndexController` 비어 있음 | 모든 화면 경로가 404 | `/`, `/posts/save`, `/posts/update/{id}` 매핑 추가 | ✅ |
| P1-9 | `UserDetailsService` 부재 | 자체 계정 로그인 불가 | `UserRepository.findByEmail` 기반 구현 | ✅ |
| P1-10 | `UserRepository.findByEmail` 부재 | 인증·작성자 조회 불가 | 메서드 추가 | ✅ |
| P1-11 | 컨트롤러에 `@Valid` 없음 | 검증 스타터가 있어도 검증 미실행 | `@RequestBody` 앞에 `@Valid` 추가 | ✅ |
| P1-12 | 예외 처리기 부재 | 없는 글 조회 시 500 + 스택 트레이스 | `@RestControllerAdvice` 추가 | ✅ **해결(2026-09-09 추가 구현)** — `ApiExceptionHandler` 추가. 상세는 8.7절 |

### P2 — 설계·보안 개선 — 이번 범위 밖 (일부는 부수적으로 해결됨)

| ID | 항목 | 조치 | 상태 |
| --- | --- | --- | --- |
| P2-1 | `Post.user`가 `EAGER` | `fetch = FetchType.LAZY` + `JOIN FETCH` 조회 | ✅ P0 작업 중 함께 해결 (Post 파일을 어차피 수정해야 했음) |
| P2-2 | `User.email` 유니크 제약 없음 | `@UniqueConstraint` 추가 | ✅ 해결 |
| P2-3 | 작성자를 클라이언트가 전송 (`author`) | 인증 주체에서 결정하도록 변경 | ✅ 해결 |
| P2-4 | 수정/삭제 권한 검증 없음 | 작성자 본인 또는 `ADMIN`만 허용 | ✅ 해결(2026-09-09 추가 구현) — `PostService.validateOwner()`가 `AccessDeniedException`을 던지고, `SecurityConfig`의 필터 체인이 이를 자동으로 `403`으로 변환. 타인 글 수정/삭제 시도 curl로 `403` 확인, 본인 글은 정상 수정/삭제됨을 확인 |
| P2-5 | OAuth2 링크가 404 | `/login`으로 교체 또는 OAuth2 클라이언트 도입 결정 | ✅ `/login`으로 교체 |
| P2-6 | `User.posts` 양방향 미사용 | 제거 검토 | ✅ 해결(2026-09-09 추가 구현) — 필드 제거. 상세는 8.12절 |
| P2-7 | `open-in-view` 기본 활성 | `false`로 변경 (DTO 변환 완료 후) | ✅ 해결(2026-09-09 추가 구현) — 상세는 8.12절 |
| P2-8 | `group = "com"` | `com.kraft`로 변경 | ✅ 해결(2026-09-09 추가 구현) — `./gradlew properties`로 `group: com.kraft` 확인. 상세는 8.11절 |
| P2-9 | 컬럼 길이 미지정 | `password`/`email`/`name`/`picture` 길이 명시 | ✅ 해결(2026-09-09 추가 구현) — `Post.picture`도 500자로 지정. 상세는 8.12절 |
| P2-10 | 목록 전체 조회 | `Pageable` 기반 페이징 | ✅ 해결(2026-09-09 추가 구현) — `PostRepository.findAllDesc(Pageable)`, API/화면 모두 적용. 상세는 8.6절 |
| P2-11 | `/api/v1/posts/list` | `GET /api/v1/posts`로 정리 | ✅ 해결(2026-09-09 추가 구현). 상세는 8.12절 |
| P2-12 | `label for` 속성 오류 | `post-update.html`의 3개 `label` 정정 | ✅ 해결 |
| P2-13 | CDN 무결성 속성 없음 | `integrity`/`crossorigin` 추가 | ✅ 해결(2026-09-09 추가 구현) — 3개 태그 모두 실측 SHA-384 해시 적용. 상세는 8.9절 |
| P2-14 | 프로파일 미분리 | `local`/`prod` 분리 | ✅ 해결(2026-09-09 추가 구현) — `application.yml`(공통)+`application-local.yml`+`application-prod.yml`. 상세는 8.8절 |
| P2-15 | 기능 테스트 부재 | 계층별 슬라이스 테스트 추가 | ✅ 해결(2026-09-09 추가 구현) — 44개 테스트(서비스/리포지토리/웹). 상세는 8.10절 |

### P3 — 2단계 기능 — 실제 이메일 인증 발송만 남고 나머지 전부 해결(소셜 로그인은 제외 결정)

| ID | 항목 | 상태 |
| --- | --- | --- |
| P3-1 | `Comment` 엔티티 완성 (`@GeneratedValue`, `BaseEntity` 상속, `Post`/`User` 연관관계) | ✅ 해결(2026-09-10 추가 구현). 상세는 8.13절 |
| P3-2 | `CommentRepository`, `CommentService`, `CommentApiController` | ✅ 해결(2026-09-10 추가 구현). 상세는 8.13절 |
| P3-3 | 댓글 화면 및 AJAX | ✅ 해결(2026-09-10 추가 구현, 수정 UI는 의도적으로 제외 — 8.13절 참고) |
| P3-4 | 이메일 인증 플로우 (`GUEST` → `USER` 승격) | 부분 해결 — `UserService.promoteToUser()` 메서드는 존재하나 실제 토큰 발급/메일 발송/컨트롤러 노출은 없음(여전히 미해결) |
| P3-5 | 게시글 사진 업로드 (`Post.picture` 실제 활용) | ✅ 해결(2026-09-10 추가 구현) — 로컬 디스크 저장 + `/images/**` 서빙. 상세는 8.14절 |
| P3-6 | `web/dto/user` 패키지 DTO 작성 (현재 디렉터리만 존재) | ✅ 해결 — `SignUpRequestDto`(record) 추가 |
| P3-7 | 회원가입 HTML 화면 | ✅ 해결(2026-09-10 추가 구현) — `templates/user/signup.html`. 상세는 8.13절 |
| P3-8 | 소셜 로그인(OAuth2) | ❌ **사용자가 명시적으로 제외 결정(2026-09-10)** — "OAuth 인증방식은 제거한다." 코드상으로도 애초에 도입된 적이 없어(P0/P1에서 이미 `/login`으로 대체) 실제로 제거할 코드는 없었다. 향후 재검토 시에도 이 결정을 기본값으로 삼는다. |
| P3-9 | 비밀번호 변경 HTML 화면 | ✅ 해결(2026-09-10 추가 구현) — `UserService.changePassword()`가 현재 비밀번호 검증 로직을 갖추도록 함께 보강됨. 상세는 8.14절 |

## 8.3 권장 작업 순서 — ✅ 아래 그대로 수행 완료 (P1-12는 2026-09-09 추가 구현으로 완료)

### 1단계 — 빌드 복구 ✅

1. `build.gradle.kts`의 Lombok 스코프 수정 (P0-1)
2. `PostsApiController` → `PostApiController` 클래스명 변경, 필드명 `postService`로 정리 (P0-2)
3. `Post`에 `@Getter`, `@Builder` 생성자, `update()` 메서드 추가 (P0-5, P0-6)
4. DTO 4종 구현 — 실제로는 record로 구현 (P0-4)
5. `PostService` 5개 메서드 구현 (P0-3)
6. `./gradlew compileJava` 통과 확인 — ✅ 통과

### 2단계 — 기동 가능 상태 ✅

7. `JpaConfig`(`@EnableJpaAuditing`) 추가 (P1-2)
8. `application.yml`에 DataSource, `ddl-auto`, `session.jdbc.initialize-schema` 보강, `dialect` 제거 (P1-5~7)
9. `SecurityConfig` 작성 (P1-3)
10. `./gradlew bootRun` 기동 및 `contextLoads()` 통과 확인 — ✅ 통과

### 3단계 — 화면 동작 ✅

11. 템플릿 5개 Thymeleaf 전환 (P1-1)
12. `IndexController` 매핑 구현 (P1-8)
13. CSRF 토큰 헤더 주입 (P1-4)
14. `index.js`에서 `author` 전송 제거 (P2-3)

### 4단계 — 인증 완성 ✅

15. `UserRepository.findByEmail`/`existsByEmail` 추가 (P1-10)
16. `User`에 `@Builder`, `changePassword()`, `promoteToUser()` 추가
17. `UserService` 회원가입/비밀번호 변경 구현
18. `UserDetailsService` 구현. **로그인 화면은 Spring Security 기본 로그인 페이지를 그대로 사용**했다(전용 템플릿 미제작, P2) (P1-9)
19. 게시글 수정/삭제 권한 검증 (P2-4) — ✅ **2026-09-09 추가 구현 완료** (아래 8.5절 참고)

### 5단계 — 품질 강화 (미수행, P2로 이월)

20. `@Valid` (P1-11) — ✅ 완료 / `@RestControllerAdvice` (P1-12) — ✅ 2026-09-09 추가 구현 완료 (8.7절)
21. `LAZY` 전환 + `JOIN FETCH` (P2-1) — ✅ 1단계에서 함께 완료
22. 프로파일 분리 (P2-14) — ❌ 미수행
23. 계층별 테스트 작성 (P2-15) — ✅ 2026-09-09 추가 구현 완료 (8.10절)

### 6단계 — 2단계 기능 (미수행, P3로 이월)

24. P3 항목 순차 진행 — `SignUpRequestDto`(P3-6)만 부수적으로 완료, 나머지는 그대로 남음

## 8.4 검증 체크리스트 — 실제 확인 결과

각 단계 완료 시 아래 명령으로 확인했다.

```powershell
.\gradlew compileJava      # ✅ 통과
.\gradlew test             # ✅ 통과
.\gradlew bootRun          # ✅ 정상 기동, curl로 브라우저 시나리오 대체 검증
```

| 확인 항목 | 기대 결과 | 실제 결과 |
| --- | --- | --- |
| 비로그인으로 `/` 접속 | 게시글 목록이 보임 (기획: 모든 사용자 조회 가능) | ✅ `200`, 목록 정상 렌더링 |
| 비로그인으로 글 등록 시도 | 로그인 페이지로 리다이렉트 | ✅ CSRF 토큰 없으면 `403`, 토큰 있어도 미인증이면 `302`(로그인 페이지로) |
| 로그인 후 글 등록 | 목록에 반영, `created_at`/`updated_at`이 `null`이 아님 | ✅ 목록에 반영 확인. `created_at`/`updated_at` 값 자체는 API 응답에 포함하지 않아 별도 미확인(H2 콘솔에서는 채워짐을 스키마로 확인) |
| 글 수정 | `updated_at`만 갱신됨 | ✅ 수정 반영 확인(정확한 타임스탬프 diff까지는 미검증) |
| 타인 글 수정 시도 | 403 | ✅ **2026-09-09 구현 완료** — userB가 userA의 글을 PUT/DELETE 시도 시 둘 다 `403 Forbidden`, 원본 데이터 변경 없음 확인 |
| 앱 재기동 (H2 인메모리) | 데이터 초기화 (정상 동작) | ✅ `ddl-auto: create-drop`으로 재기동 시 초기화 확인 |
| SQL 로그 | `posts` 조회 시 `users` 추가 조회가 N번 발생하지 않음 | ✅ `JOIN FETCH`로 단일 쿼리 확인 |

상세 curl 시나리오와 실제 응답은 [09. 구현 요약 4절](09-implementation-summary.md#4-실제-검증-curl-기반-e2e) 참고.

## 8.5 [추가 구현] P2-4 작성자 권한 검증 (2026-09-09)

`PostService.update()`/`delete()`가 `Authentication`을 추가로 받아 게시글 작성자 본인이거나
`ROLE_ADMIN` 권한을 가진 경우에만 처리하도록 구현했다.

```java
private void validateOwner(Post post, Authentication authentication) {
    boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getKey()));
    boolean isOwner = post.getUser() != null
            && post.getUser().getEmail().equals(authentication.getName());

    if (!isAdmin && !isOwner) {
        throw new AccessDeniedException("작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=" + post.getId());
    }
}
```

**당시(P1-12 미해결 시점) 403을 반환한 방법**: `AccessDeniedException`은 Spring Security의
표준 예외이며, `SecurityFilterChain`에 기본 포함되는 `ExceptionTranslationFilter`가 컨트롤러/
서비스에서 던져진 이 예외를 가로채 `AccessDeniedHandler`(기본값)로 위임해 자동으로
`403 Forbidden`을 반환한다. 당시엔 별도 `@ExceptionHandler`가 없어도 인가 실패는 이 경로로
처리되었다. **이후 8.7절에서 `ApiExceptionHandler`(`@RestControllerAdvice`)를 추가하면서
`AccessDeniedException`도 그 안에서 함께 처리하도록 흡수했다** — 나머지 API 오류와 동일한
JSON 형식(`ProblemDetail`)으로 통일하기 위함이며, 여전히 `403`을 반환한다는 결과는 동일하다.

**검증**(curl E2E): `userA`가 글을 작성한 뒤 `userB`로 로그인해 같은 글을 PUT/DELETE 시도 →
둘 다 `403`, `GET /api/v1/posts/1`로 원본 데이터가 그대로임을 확인. 이후 `userA`(작성자 본인)가
같은 글을 수정·삭제하면 정상적으로 `200`이 반환됨을 확인했다.

## 8.6 [추가 구현] P2-10 페이징 (2026-09-09)

`PostRepository.findAllDesc()`를 `List<Post>` 대신 `Page<Post> findAllDesc(Pageable pageable)`로
변경하고, `JOIN FETCH`(작성자)와 별도의 `countQuery`를 함께 지정했다.

```java
@Query(value = "SELECT p FROM Post p JOIN FETCH p.user ORDER BY p.id DESC",
        countQuery = "SELECT COUNT(p) FROM Post p")
Page<Post> findAllDesc(Pageable pageable);
```

`Post.user`는 `@ManyToOne`(to-one)이라 `JOIN FETCH` + `LIMIT`/`OFFSET` 페이징이 Hibernate의
"컬렉션 fetch와 페이징 동시 사용 시 메모리 페이징" 경고 없이 DB 레벨에서 그대로 동작한다
(컬렉션(`@OneToMany`/`@ManyToMany`) fetch였다면 문제가 됐을 것).

응답은 Spring Data의 `Page`를 직접 직렬화하지 않고(Spring이 로그로 권장하지 않는 방식이라고
경고하는 패턴), `content`/`page`/`size`/`totalElements`/`totalPages`/`first`/`last` 7개 필드만
담은 `PostsPageResponseDto`(record)로 감싸 API 계약을 명시적으로 고정했다.

`GET /api/v1/posts/list`는 `@PageableDefault(size = 10)`로 `?page=`/`?size=` 쿼리 파라미터를
받고, 화면(`GET /`)도 동일하게 `Pageable`을 받아 페이지당 10건, Bootstrap 페이지네이션 UI로
이전/페이지 번호/다음 링크를 렌더링한다.

**[해결한 버그] 음수 페이지 500 에러**: 처음에는 `IndexController`가 `@RequestParam int page`를
받아 `PageRequest.of(page, size)`를 직접 생성했는데, 1페이지에서 "이전" 버튼이 `?page=-1`로
링크되어 있고(Bootstrap의 `disabled` 클래스는 시각적 표시일 뿐 클릭을 막지 않음) 이를 클릭하면
`PageRequest.of(-1, ...)`가 `IllegalArgumentException`을 던져 `500`이 발생했다. `PostApiController`처럼
`Pageable`을 `@PageableDefault`로 직접 받도록 바꿔 Spring Data의 `PageableHandlerMethodArgumentResolver`가
음수/범위초과 페이지 번호를 자동으로 안전하게 처리(0으로 보정)하도록 수정해 해결했다.

**검증**(curl E2E): 게시글 25건 생성 → `GET /api/v1/posts/list`(기본 size=10)에서 `totalPages: 3`,
1페이지는 ID 25~16(`first: true, last: false`), 3페이지는 ID 5~1(`first: false, last: true`) 확인.
화면(`GET /`)에서도 페이지당 10행, 페이지네이션 링크(1/2/3, 이전/다음) 정상 렌더링 확인.
`?page=-1`, `?page=999`(범위초과) 모두 API·화면에서 `500` 없이 안전하게 처리됨을 재확인했다.

## 8.7 [추가 구현] P1-12 전역 예외 처리기 (2026-09-09)

`com.kraft.web.exception.ApiExceptionHandler`를 신설했다. Spring Boot 4가 지원하는
`ProblemDetail`(RFC 9457)을 그대로 반환하도록 구현해, 별도의 커스텀 오류 응답 클래스나
새 의존성 없이(`spring-web`에 이미 포함) 상태 코드와 `Content-Type: application/problem+json`이
자동으로 설정된다.

```java
@Slf4j
@RestControllerAdvice(basePackages = "com.kraft.web.api")
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedJson(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외가 발생했습니다.", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }
}
```

**설계 결정**

- **`basePackages = "com.kraft.web.api"`로 범위를 REST 컨트롤러에 한정**했다. `@RestControllerAdvice`를
  전역으로 두면 `IndexController`(화면 컨트롤러)에서 발생하는 예외도 JSON `ProblemDetail`로
  바뀌어 버리는데, 브라우저가 렌더링하는 Thymeleaf 뷰 요청에 JSON을 내려주는 것은 부적절하다.
  실제로 `GET /posts/update/999`(존재하지 않는 글)를 호출해 `IndexController`가 여전히 이 범위
  밖임을 확인했다(`500`, Spring Boot 기본 오류 응답 — 이전과 동일, 회귀 없음).
- **`AccessDeniedException`을 흡수**했다. 이 핸들러가 없으면 Spring Security의
  `ExceptionTranslationFilter`가 대신 처리해도 결과는 동일하게 `403`이지만, 응답 본문 형식이
  Spring Boot 기본 오류 형식(`{"timestamp","status","error","path"}`)이 되어 다른 API 오류
  (`ProblemDetail` 형식)와 달라진다. 이 핸들러를 두면 모든 API 오류가 동일한 JSON 형식으로
  통일된다. **주의**: catch-all `@ExceptionHandler(Exception.class)`를 함께 등록했으므로,
  `AccessDeniedException`을 명시적으로 처리하지 않으면 `403`이어야 할 응답이 catch-all에
  잡혀 `500`으로 바뀌는 회귀가 생길 뻔했다 — 구현 시 이 순서를 반드시 지켜야 한다.
- **catch-all(`Exception.class`)은 클라이언트에 상세 원인을 노출하지 않고** 서버 로그에만
  전체 스택 트레이스를 남긴다(`log.error(...)`). 이 경로는 의도적으로 curl로 재현하지
  않았다(임의로 애플리케이션을 깨뜨려야 하므로) — 코드 리뷰로만 검증했다.

**검증**(curl E2E, 로그인·CSRF 토큰 포함):

| 상황 | 이전 | 현재 |
| --- | --- | --- |
| 존재하지 않는 게시글 조회(`GET /api/v1/posts/999`) | `500` + 스택 트레이스 | `400` `{"detail":"해당 게시글이 없습니다. id=999", "status":400, "title":"Bad Request", ...}` |
| 잘못된 JSON 본문 | `500` | `400` `{"detail":"요청 본문을 읽을 수 없습니다.", ...}` |
| 빈 제목으로 등록(`@NotBlank` 위반) | 검증은 되지만 오류 형식 불명확 | `400` `{"detail":"title: 제목은 필수입니다.", ...}` |
| 이메일 중복 가입 | `500` | `400` `{"detail":"이미 가입된 이메일입니다. email=...", ...}` |
| 타인 글 삭제 시도(`AccessDeniedException`) | `403`(Boot 기본 오류 형식) | `403` `{"detail":"작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=1", "status":403, "title":"Forbidden", ...}` — 형식만 통일, 상태 코드는 동일 |
| 정상 흐름(목록 조회, 화면 렌더링) | 정상 | 회귀 없음 재확인 |

## 8.8 [추가 구현] P2-14 프로파일 분리 (2026-09-09)

단일 `application.yml`을 공통 파일 + `application-local.yml` + `application-prod.yml` 3개로
분리했다. `application.yml`에 `spring.profiles.default: local`을 지정해, 프로파일을 명시하지
않으면 자동으로 `local`이 적용되도록 해 기존 `./gradlew bootRun`/`./gradlew test` 사용법을
그대로 유지했다. 각 파일의 전체 내용은 [07장 7.4절](07-configuration.md#74-프로파일-분리--해결됨-2026-09-09-추가-구현) 참고.

`local`과 `prod`의 실질적 차이:

| 항목 | local | prod |
| --- | --- | --- |
| DataSource | H2 인메모리(`jdbc:h2:mem:kraft;MODE=MariaDB`), 자격 증명 하드코딩 | `${DB_URL}`/`${DB_USERNAME}`/`${DB_PASSWORD}` 환경변수, MariaDB 드라이버 |
| `ddl-auto` | `create-drop`(재기동마다 초기화) | `validate`(스키마 불일치 시 기동 실패 — 애플리케이션이 운영 스키마를 만들거나 지우지 않음) |
| H2 콘솔 | 활성화 | **비활성화** — `SecurityConfig`의 `/h2-console/**` permitAll·CSRF 예외가 가리킬 대상 자체가 없어짐 |
| 세션 스키마 초기화 | `always` | `never`(사전에 준비된 테이블 사용) |
| Thymeleaf 캐시 | `false`(즉시 반영) | `true` |

**검증**(실제 3가지 시나리오, 이 환경에 운영 MariaDB가 없어 datasource 연결 자체는 H2로 대체 검증):

1. **무프로파일 기동(회귀 확인)**: `./gradlew bootRun` → 로그에
   `"falling back to 1 default profile: 'local'"`, `/`·`/h2-console`·`/api/v1/posts/list`
   모두 이전과 동일하게 정상 동작.
2. **`prod` + 환경변수 없음(fail-fast 확인)**: `./gradlew bootRun --args="--spring.profiles.active=prod"`
   → 로그에 `"The following 1 profile is active: 'prod'"` 확인 후, `${DB_URL}`이 해석되지
   않은 채 드라이버에 전달되어 `Driver org.mariadb.jdbc.Driver claims to not accept jdbcUrl, ${DB_URL}`
   오류로 기동 자체가 실패. 운영 자격 증명 없이는 절대 뜨지 않음을 확인.
3. **`prod` + H2로 datasource만 임시 대체(그 외 prod 설정 검증)**: `--spring.profiles.active=prod`에
   `--spring.datasource.url=jdbc:h2:mem:...`, `driver-class-name=org.h2.Driver` 등을 함께
   넘겨 실행 → 빈 스키마에 대해 `Schema validation: missing table [comment]`로 실패
   (`ddl-auto: validate`가 실제 적용되어 `create-drop`이 아님을 증명) + 로그에
   `"H2 console available"` 문구가 전혀 나타나지 않음(`h2.console.enabled: false` 적용 증명).

`prod` 프로파일이 실제 MariaDB에 끝까지 정상 접속하는 것은 검증하지 못했다(운영 DB 부재) —
`ddl-auto: validate`의 스키마 불일치 감지, `h2.console.enabled: false` 적용은 확인했으므로
남은 미검증 영역은 실제 네트워크/드라이버 수준의 MariaDB 접속뿐이다.

## 8.9 [추가 구현] P2-13 CDN 무결성 속성 (2026-09-09)

`layout/header.html`(Bootstrap CSS)과 `layout/footer.html`(jQuery, Bootstrap JS) 3개 태그에
`integrity`(SRI, Subresource Integrity)와 `crossorigin="anonymous"`를 추가했다.

**해시를 구한 방법 — 기억이나 문서에서 베끼지 않고 실측했다.** SRI 해시가 실제 파일과 1비트라도
다르면 브라우저가 리소스 로딩 자체를 차단해(무결성 검증 실패) 화면이 깨지는 심각한 회귀로
이어질 수 있다. 이를 피하기 위해 템플릿이 실제로 참조하는 URL에서 파일을 직접 내려받아
`openssl dgst -sha384`로 계산했다.

```bash
curl -sSL -o jquery-3.3.1.min.js "https://code.jquery.com/jquery-3.3.1.min.js"
curl -sSL -o bootstrap.min.js "https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/js/bootstrap.min.js"
curl -sSL -o bootstrap.min.css "https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css"

for f in jquery-3.3.1.min.js bootstrap.min.js bootstrap.min.css; do
  openssl dgst -sha384 -binary "$f" | openssl base64 -A
done
```

| 파일 | SRI 해시(sha384) |
| --- | --- |
| `jquery-3.3.1.min.js` | `sha384-tsQFqpEReu7ZLhBV2VZlAu7zcOV+rXbYlF2cqB8txI/8aZajjp4Bqd+V6D5IgvKT` |
| `bootstrap.min.js` (4.3.1) | `sha384-JjSmVgyd0p3pXB1rRibZUAYoIIy6OrQ6VrjIEaFf/nJGzIxFDsf4x0xIM+B07jRM` |
| `bootstrap.min.css` (4.3.1) | `sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T` |

Bootstrap 두 값은 공식 문서에 게시된 값과 실측치가 정확히 일치했다. jQuery 값은 실측 전
기억에 의존했다면 다른(예: sha256 계열) 값을 잘못 넣었을 수 있었다 — 실측 검증의 필요성을
보여주는 사례다.

**검증**(curl + `bootRun`): `GET /`, `GET /posts/save` 응답 HTML에 3개 태그 모두
`integrity="sha384-..."` `crossorigin="anonymous"`가 정확히 렌더링됨을 확인했다. curl은
SRI를 강제하지 않으므로(브라우저 전용 보안 기능) 실제 검증은 렌더링된 해시값이 위 표의
실측치와 정확히 일치하는지 diff로 확인하는 방식으로 대체했다. `./gradlew compileJava test`도
회귀 없이 통과했다.

## 8.10 [추가 구현] P2-15 계층별 테스트 코드 (2026-09-09)

의존성에 이미 선언돼 있던 테스트 스타터(`webmvc-test`, `security-test`, `thymeleaf-test`,
`validation-test`)를 실제로 활용해 계층별 테스트 44개를 작성했다. `spring-boot-starter-data-jpa-test`는
선언되어 있지 않아 신규로 추가했다 — Boot 4.1.1에서 실제 존재/해석되는지
`./gradlew dependencies`로 먼저 확인한 뒤 추가했다(존재하지 않는 아티팩트를 추측으로 적어
빌드를 깨뜨리지 않기 위함).

### 구성

| 파일 | 유형 | 개수 | 대상 |
| --- | --- | --- | --- |
| `service/post/PostServiceTest.java` | Mockito 단위 테스트 | 10 | `save`/`update`/`delete`(권한 검증 포함)/`findById`/`findAllDesc` |
| `service/user/UserServiceTest.java` | Mockito 단위 테스트 | 5 | `signUp`(중복 검사·비밀번호 인코딩)/`changePassword`/`promoteToUser` |
| `domain/post/PostRepositoryTest.java` | `@DataJpaTest` | 4 | `findAllDesc`의 정렬/JOIN FETCH/페이징, `BaseEntity` 감사 필드 |
| `domain/user/UserRepositoryTest.java` | `@DataJpaTest` | 5 | `findByEmail`/`existsByEmail`, **`email` 유니크 제약을 실제 DB 예외로 검증** |
| `web/api/PostApiControllerTest.java` | `@WebMvcTest` + 실제 `SecurityConfig` | 8 | CSRF/인증/인가, `ApiExceptionHandler`의 400/403 응답 |
| `web/api/UserApiControllerTest.java` | `@WebMvcTest` + 실제 `SecurityConfig` | 5 | 회원가입 검증 규칙, CSRF, 중복 이메일 |
| `web/IndexControllerTest.java` | `@WebMvcTest` + 실제 `SecurityConfig` | 6 | 화면 라우팅, **`page=-1`/`page=999` 회귀 방지** |

### 설계에서 실제로 부딪힌 문제들

1. **Boot 4의 테스트 애노테이션 패키지 이동.** `@WebMvcTest`/`@DataJpaTest`/`TestEntityManager`를
   기존 Boot 3 관례대로 `org.springframework.boot.test.autoconfigure.web.servlet`/`orm.jpa`에서
   import했더니 컴파일 오류가 났다. Boot 4.1.1은 이 클래스들을 기능별 패키지로 재배치했다 —
   실제 JAR을 `unzip -l`로 열어 정확한 위치를 확인했다.

   | 클래스 | Boot 3 관례(틀림) | Boot 4.1.1 실제 위치 |
   | --- | --- | --- |
   | `@WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
   | `@DataJpaTest` | `org.springframework.boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.data.jpa.test.autoconfigure` |
   | `TestEntityManager` | `org.springframework.boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.jpa.test.autoconfigure` |

2. **`@MockBean`이 아니라 `@MockitoBean`.** Spring Boot 3.4/Spring Framework 6.2부터
   `@MockBean`/`@SpyBean`이 `org.springframework.test.context.bean.override.mockito.MockitoBean`/
   `MockitoSpyBean`으로 대체(구 애노테이션은 지원 중단)되었다. Boot 4.1.1인 이 프로젝트는
   신규 애노테이션을 사용했다.

3. **`@DataJpaTest`는 `@EnableJpaAuditing`이 선언된 `JpaConfig`를 자동으로 포함하지 않는다.**
   `@DataJpaTest`의 컴포넌트 스캔은 Entity/Repository 관련 빈으로 제한되어, 일반
   `@Configuration` 클래스는 기본적으로 빠진다. `@Import(JpaConfig.class)`를 명시해야
   `createdAt`/`updatedAt` 자동 기록을 실제로 검증할 수 있었다(안 했다면 테스트가 "우연히
   통과"한 게 아니라 애초에 `null`이어서 실패했을 것 — 검증 자체가 무의미해지지 않도록 확인).

4. **`@WebMvcTest`에 실제 `SecurityConfig`를 명시적으로 `@Import`했다.** `@WebMvcTest`가
   커스텀 `@Configuration` 클래스(순수 `SecurityFilterChain` 빈 정의)를 기본적으로 포함하는지는
   Spring 버전에 따라 애매한 부분이라, 확실성을 위해 매번 명시적으로 `@Import(SecurityConfig.class)`
   했다. 이 덕분에 CSRF·인증·인가 규칙이 실제 운영 설정 그대로 테스트에 적용된다(가짜 보안
   설정이 아님).

5. **MockMvc는 처리되지 않은 예외를 500 응답으로 바꿔주지 않는다.** `IndexControllerTest`에서
   "없는 글 수정 화면 → 500"을 처음에 `status().isInternalServerError()`로 검증하려다 실패했다.
   실제 서버(Tomcat, `bootRun`)는 컨테이너 레벨에서 미처리 예외를 500 응답으로 변환하지만,
   MockMvc는 컨테이너 없이 `TestDispatcherServlet`만으로 동작해 처리되지 않은 예외를 감싸서
   그대로 **던진다**. `assertThatThrownBy(...).hasRootCauseInstanceOf(IllegalArgumentException.class)`
   형태로 수정해 실제 MockMvc 동작에 맞췄다(이 차이 자체가 애플리케이션 버그는 아니었다).

6. **`redirectedUrlPattern("**/login")`이 실패했다.** 실제 리다이렉트 URL이 정확히 `/login`
   (앞에 아무 경로도 없음)이라 glob 패턴 `**/login`과 매칭되지 않았다. `redirectedUrl("/login")`
   (정확히 일치)로 바꿔 해결했다 — 이 역시 테스트 코드의 오류였지 애플리케이션 문제는 아니었다.

### 실행 결과

```
44 tests completed, 0 failed
```

`./gradlew clean test`로 재확인했다. 리포지토리 테스트가 `email` 유니크 제약을 실제
`DataIntegrityViolationException`으로, 컨트롤러 테스트가 CSRF/인증/인가/전역 예외 처리기를
실제 `SecurityConfig`+`ApiExceptionHandler` 조합으로, `IndexControllerTest`가 과거 실제로
`500`을 유발했던 `page=-1` 버그(P2-10, 8.6절)의 회귀를 각각 자동으로 검증한다 — 이후에는
`bootRun`+curl로 수동 재현하지 않아도 `./gradlew test` 한 번으로 이 모든 것이 여전히
성립하는지 확인할 수 있다.

## 8.11 [추가 구현] P2-8 group 좌표 정정 (2026-09-09)

`build.gradle.kts`의 `group = "com"`을 `group = "com.kraft"`로 변경했다. 패키지 구조는
이미 `com.kraft`였으므로 Maven/Gradle 좌표(`com:kraft:...` → `com.kraft:kraft:...`)만
패키지와 일치시킨 것이다.

**영향 범위를 정확히 확인했다**: `group`은 이 프로젝트를 다른 프로젝트가 의존성으로 참조할 때
쓰는 좌표(퍼블리싱 메타데이터)에만 영향을 준다. JAR 파일명(`kraft-0.0.1-SNAPSHOT.jar`)은
`settings.gradle.kts`의 `rootProject.name`으로 결정되고, 패키지 구조는 소스 코드 자체(이미
`com.kraft`)로 결정되므로 둘 다 `group` 변경과 무관하다 — 즉 **런타임 동작에는 영향이 없는
순수 메타데이터 변경**이다.

**검증**: `./gradlew properties`로 `group: com.kraft`가 실제로 반영됐음을 확인했고,
`./gradlew clean build`로 JAR이 정상 생성됨(매니페스트에 `Start-Class: com.kraft.KraftApplication`
그대로)을, `./gradlew clean test`로 44개 테스트 전부 회귀 없이 통과함을 확인했다.

## 8.12 [추가 구현] P2-1/P2-6/P2-7/P2-9/P2-11 나머지 정리 (2026-09-09)

**P2 표의 남은 4개 항목을 한 번에 처리했다.** 사전에 사용처를 grep으로 확인해 안전함을
검증한 뒤 진행했다(`User.posts`는 프로젝트 어디서도 참조되지 않음, `/api/v1/posts/list`는
`index.js`가 호출하지 않고 `IndexController`가 서버사이드에서 `PostService`를 직접 호출함을
확인).

### P2-7: `open-in-view: false`

공통 `application.yml`에 `spring.jpa.open-in-view: false`를 추가했다. `bootRun` 로그에서
기존에 항상 출력되던 `spring.jpa.open-in-view is enabled by default` 경고가 사라졌음을
확인했고, `GET /`(목록 화면)이 여전히 `200`으로 정상 렌더링됨을 확인해
`LazyInitializationException`이 발생하지 않음을 검증했다 — `IndexController`가 이미
`PostService`의 DTO(record) 반환값만 `Model`에 담고 엔티티를 뷰까지 전달하지 않으므로
안전하게 끌 수 있었다.

### P2-6: `User.posts` 미사용 양방향 연관관계 제거

`User`에서 `@OneToMany(mappedBy = "user") private List<Post> posts;` 필드와 관련 `import`를
제거했다. 제거 전 `grep -rn "getPosts()\|\.posts\b" src/`로 프로젝트 전체에서 이 필드가
어디서도 사용되지 않음을 먼저 확인했다.

### P2-9: `Post.picture` 컬럼 길이 지정

`Post.picture`에 `@Column(length = 500)`을 추가했다(다른 필드는 이미 [03장](03-domain-model.md)에서
지정 완료). 실제 파일 경로/URL을 저장하는 용도라는 기존 설계 의도를 그대로 반영했다.

### P2-11: `/api/v1/posts/list` → `GET /api/v1/posts`

`@GetMapping("/api/v1/posts/list")`를 `@GetMapping("/api/v1/posts")`로 변경했다. `POST
/api/v1/posts`(등록)와 같은 경로를 HTTP 메서드로만 구분하는 REST 관례에 맞춘 것이다.

**검증 중 실제로 발견한 부작용**: 옛 경로 `/api/v1/posts/list`를 호출하면 이제
`GET /api/v1/posts/{id}`에 `id="list"`가 매칭되어 `Long` 타입 변환에 실패하는데, 이 예외
(`MethodArgumentTypeMismatchException`)를 `ApiExceptionHandler`가 처리하지 않고 있어
catch-all에 잡혀 **500**이 되는 것을 curl로 직접 확인했다. 클라이언트 입력 오류이므로 500보다
400이 맞다고 판단해, `ApiExceptionHandler`에 `MethodArgumentTypeMismatchException` 전용
핸들러를 추가로 구현했다(400 + `"요청 값의 형식이 올바르지 않습니다: id"`). 이 수정 후
옛 경로 호출 시 `400`을 반환함을 재확인했고, 회귀 테스트(`PostApiControllerTest`)도 추가했다.

### P2-1 재확인

`Post.user`의 `fetch = FetchType.LAZY`와 `PostRepository`의 `JOIN FETCH`는 P0 단계에서 이미
반영되어 있었다(8.2절 P2-1 표 항목대로). 이번 작업에서 별도 변경은 없었고, 위 P2-7 검증
과정에서 목록 화면이 여전히 N+1 없이 정상 렌더링됨을 다시 한번 확인했다.

### 종합 검증

`./gradlew clean test` → **45개 테스트 전부 통과**(기존 44개 + `MethodArgumentTypeMismatchException`
회귀 테스트 1개 추가). `bootRun` 후 curl로 회원가입 → 로그인 → `picture` 필드가 포함된 글
등록 → 목록/화면 렌더링(open-in-view false 상태) → 새 경로(`GET /api/v1/posts`) 정상 동작 →
옛 경로(`GET /api/v1/posts/list`) 400 반환까지 전체 흐름을 재확인했다.

## 8.13 [추가 구현] P3 댓글(Comment) 기능 + 회원가입 화면 (2026-09-10)

**범위 확정**: P3 전체가 아니라 사용자가 선택한 두 항목(댓글 기능, 회원가입 화면)만 진행했다.
실제 이메일 인증 발송·소셜 로그인은 새 의존성(`spring-boot-starter-mail`,
`spring-boot-starter-oauth2-client`)이 필요해 이번 범위에서 명시적으로 제외했다(P3-4 부분 해결
상태 유지, P3-8 신설·미해결).

### 사전 검증 (구현 전에 먼저 확인한 것)

- `grep -rn "getPosts()\|\.posts\b"`로 `User.posts`(P2-6에서 이미 제거됨)가 남아 있지 않음을
  재확인 — 댓글이 `User`가 아닌 `Post`/`Comment` 양방향으로만 연관관계를 맺으므로 무관함을 확인.
- `/api/v1/posts/list`처럼 댓글 API도 `SecurityConfig`의 기존 매처와 충돌하지 않는지 매처 표로
  사전 검증([04장 4.8절](04-architecture-and-layers.md#48-댓글comment-계층--구현-완료-2026-09-10)) →
  **`SecurityConfig.java`는 이번 작업에서 한 줄도 수정하지 않았다.**
- `PostServiceTest`가 `AccessDeniedException`을 `.isInstanceOf(...)`로만 검증(메시지 미검증)하고
  `PostApiControllerTest`가 `PostService`를 완전히 모킹함을 `grep`으로 확인한 뒤,
  `OwnershipPolicy` 공유 유틸 추출을 진행했다(안전성 사전 확보).

### 구현 내용

1. **`OwnershipPolicy`** (`service/support/OwnershipPolicy.java`, 신규): "작성자 본인 또는
   `ROLE_ADMIN`만 통과, 아니면 `AccessDeniedException`" 정책을 `Post`/`Comment` 양쪽이 공유하는
   순수 정적 유틸. `PostService.validateOwner()`를 1줄 위임으로 축소(메시지 동일 유지).
2. **`Comment` 엔티티 전면 재작성**: `@GeneratedValue`, `BaseEntity` 상속, `Post`/`User`
   `@ManyToOne(LAZY)` 연관관계, 테이블명 `comment` → `comments`.
3. **`CommentRepository`**: `findAllByPostIdAsc(postId)` — `JOIN FETCH c.user`, id 오름차순.
4. **DTO 3종**(record, `web/dto/comment/`): `CommentSaveRequestDto`, `CommentUpdateRequestDto`,
   `CommentResponseDto`.
5. **`CommentService`**: save/update/delete(소유권 검증)/findByPostId. 없는 대상은
   `IllegalArgumentException`(→400), 권한 없음은 `AccessDeniedException`(→403) — 기존
   `PostService` 컨벤션 그대로.
6. **`CommentApiController`**(`web.api` 패키지 → `ApiExceptionHandler` 자동 적용): 4개 엔드포인트
   (05장 5.3.6절 참고).
7. **`IndexController`**: `CommentService` 주입, `/posts/update/{id}`에 `comments` 모델 추가,
   `GET /signup` 라우팅 신설.
8. **템플릿**: `post-update.html`에 `xmlns:sec` 추가 + 댓글 목록/작성 폼, `user/signup.html`
   신규, `index.html`에 회원가입 링크.
9. **`index.js`**: `comment`(save/remove), `signup`(save) 객체 추가. 기존 `main` 객체 무변경.
10. **테스트**: `CommentServiceTest`(9), `CommentRepositoryTest`(3), `CommentApiControllerTest`(7),
    `IndexControllerTest`에 케이스 2개 추가(댓글 모델 속성, `/signup`) — 총 신규 20개.

### 설계 결정: 댓글 "수정" UI는 제외, API만 제공

인라인 수정(목록 항목을 편집 모드로 전환)은 클라이언트 상태 관리가 늘어나는데 비해, 댓글은
삭제 후 재작성으로 충분히 대체 가능한 짧은 텍스트라고 판단해 화면에서는 뺐다. `PUT
/api/v1/comments/{id}`는 API로 완성하고 테스트도 작성했다. 삭제 버튼은 소유권과 무관하게
로그인한 모든 사용자에게 노출하고(작성자 이메일이 응답 DTO에 없어 클라이언트가 소유권을 정확히
판별할 수 없음) 서버가 실제 권한을 강제한다 — `post-update.html`이 원래도 수정/삭제 버튼을
소유권과 무관하게 보여주던 것과 같은 패턴.

### 검증 결과

`./gradlew clean test` → 기존 45개 + 신규 20개 = **65개 테스트 전부 통과**.

`bootRun` 후 curl E2E(세션 쿠키·CSRF 토큰 실사용) — 13단계 전부 성공:

1. `GET /signup` → `200`, `id="btn-signup"` 포함
2. `userA` 회원가입 → `200`
3. `userA` 로그인 → `302`
4. `userA`가 게시글 작성 → `postId` 반환
5. 비로그인으로 `GET .../comments` → `200`(공개 확인)
6. `userA`가 댓글 작성 → 응답에 `author: "userA"` 정확히 반영
7~8. `userB` 가입/로그인 → `userB`가 `userA`의 댓글 삭제 시도 → **`403`**
   `{"detail":"작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=1", ...}`
9. 삭제 실패 후에도 댓글이 그대로 남아있음을 재확인
10. `userA` 본인이 삭제 → **`200`**
11. 목록이 빈 배열로 갱신됨
12. `GET /posts/update/{id}` 화면에 새 댓글의 내용(`<p>두 번째 댓글</p>`), 삭제 버튼
    (`btn-comment-delete`), 작성 폼(`id="comment-content"`)이 모두 정확히 렌더링됨
13. 비로그인으로 같은 화면 접근 시 댓글 폼·삭제 버튼이 전혀 없고 "로그인 후 댓글을 작성할 수
    있습니다." 링크만 노출됨 + `index.html`에 `/signup` 링크 렌더링 확인

## 8.14 [추가 구현] P3-5 게시글 사진 업로드 + P3-9 비밀번호 변경 화면 (2026-09-10)

**범위 재확정**: 사용자가 남은 P3 중 "게시글 사진 업로드"와 "비밀번호 변경 화면"만 진행하도록
선택했고, 소셜 로그인(OAuth2)은 "OAuth 인증방식은 제거한다"고 명시적으로 제외를 결정했다(코드상
애초에 도입된 적이 없어 실제로 제거할 것은 없었음 — P0/P1 단계에서 이미 `/login`으로 대체됨).
실제 이메일 인증 발송은 이번에도 선택되지 않아 여전히 미해결(P3-4)로 남는다.

### 게시글 사진 업로드 (P3-5)

새 의존성 없이 `spring-boot-starter-webmvc`에 이미 포함된 Multipart 지원만으로 구현했다.

- **`PostImageService`**(신규): 확장자 화이트리스트(jpg/jpeg/png/gif/webp), 5MB 크기 제한을
  검증한 뒤 `app.upload.dir`(기본값 `uploads/images`, `application.yml` 공통 설정) 아래에
  UUID 파일명으로 저장하고 `/images/{filename}` 공개 URL을 반환한다.
- **`WebConfig`**(신규, `WebMvcConfigurer`): `/images/**` 요청을 업로드 디렉터리로 매핑하는
  정적 리소스 핸들러. **`SecurityConfig`는 수정하지 않았다** — `/images/**`는 이미
  permitAll 목록에 있었다(애초에 정적 이미지 서빙을 염두에 두고 넣어뒀던 경로를 그대로 재사용).
- **`POST /api/v1/posts/images`**(`PostApiController`에 추가, multipart): `/api/v1/**`
  authenticated 규칙에 자동으로 걸려 로그인이 필요하다(이번에도 SecurityConfig 변경 없음).
  응답은 `{"url": "/images/..."}`(`ImageUploadResponseDto`).
- **화면**: `post-save.html`에 파일 입력(`<input type="file">`) 추가. `index.js`의
  `main.save()`가 파일이 선택되어 있으면 먼저 `/api/v1/posts/images`로 업로드해 URL을 받고,
  그 URL을 포함해 게시글을 등록하도록 2단계 흐름으로 바꿨다(`uploadImage()` → `doSave()`).
  `post-update.html`은 `post.picture`가 있으면 `<img>`로 표시만 한다(수정 시 사진 교체는
  이번 범위에서 제외 — 등록 시 첨부만 지원).
- **운영 환경 한계를 문서화**: `application.yml`에 "컨테이너 재배포 시 파일이 유실될 수 있으므로
  영구 볼륨이나 오브젝트 스토리지로 교체를 권장한다"는 주석을 남겼다. 로컬 디스크 저장은 학습·
  단일 서버 배포 범위에서만 안전하다.

**검증(curl E2E, Windows curl.exe가 MSYS 경로를 `-F` 옵션 안에서 인식하지 못해 `cygpath -w`로
Windows 경로 변환 후 재시도해 우회함 — 실제 애플리케이션 문제 아님)**:
- CSRF 없이 업로드 → `403` / CSRF만 있고 미인증 → `302`(로그인 페이지)
- 로그인 후 PNG 업로드 → `200` + `{"url": "/images/<uuid>.png"}`
- 반환된 URL을 직접 GET → `200`, 업로드한 파일과 동일한 바이트 크기 확인
- 허용되지 않는 확장자(`.txt`) 업로드 → `400` `{"detail":"허용되지 않는 파일 형식입니다: txt", ...}`
- 업로드 URL을 `picture`로 지정해 게시글 등록 → 단건 조회 API 응답에 `picture` 필드 정상 포함
- `GET /posts/update/{id}` 렌더링 결과에 `<img src="/images/<uuid>.png" ...>` 정확히 포함됨을 확인

### 비밀번호 변경 화면 (P3-9)

- **`UserService.changePassword()` 시그니처를 `(Long userId, String rawPassword)`에서
  `(String email, String currentPassword, String newPassword)`로 변경**하고, 현재 비밀번호를
  `passwordEncoder.matches()`로 검증한 뒤에만 변경하도록 보강했다. 기존 시그니처는 현재 비밀번호
  확인 없이 누구든 자유롭게 바꿀 수 있는 상태였는데(호출하는 곳이 없어 실제 위험은 없었음), 화면을
  붙이는 시점에 `User.java` 클래스 주석("회원정보 변경은 비밀번호 변경만 가능")이 원래 의도한
  안전장치를 함께 구현했다.
- **`ChangePasswordRequestDto`**(신규 record): `currentPassword`, `newPassword`(`@NotBlank`,
  8자 이상).
- **`PUT /api/v1/users/me/password`**(`UserApiController`에 추가): `SecurityConfig`의
  `/api/v1/users` permitAll 규칙은 **정확히 일치하는 경로에만** 적용되어(와일드카드 없음)
  `/api/v1/users/me/password`는 매치되지 않고 `/api/v1/**` authenticated로 떨어진다 — 이번에도
  **SecurityConfig 변경 없이** 의도한 인증 요구가 자동으로 적용됨을 사전에 확인하고 진행했다.
  성공 시 `204 No Content`.
- **화면**: `templates/user/change-password.html`(신규), `GET /users/me/password`
  (`IndexController`). 이 경로도 어떤 permitAll 패턴에도 걸리지 않지만 마지막
  `anyRequest().permitAll()`에 의해 화면 자체는 누구나 열람 가능하다(기존 `/posts/save`,
  `/signup`과 동일한 패턴 — 쓰기는 API가 인증을 강제).
  `index.html`의 로그인 사용자 영역에 "비밀번호 변경" 링크를 추가했다.

**검증(curl E2E)**:
1. `GET /users/me/password` → `200`, `id="btn-change-password"` 포함
2. 미인증 + CSRF만 → `302`(로그인 페이지)
3. 로그인 후 틀린 현재 비밀번호로 시도 → `400` `{"detail":"현재 비밀번호가 일치하지 않습니다.", ...}`
4. 올바른 현재 비밀번호로 변경 → `204`
5. **옛 비밀번호로 로그인 시도 → 실패**(`/login?error`로 리다이렉트) — 실제로 비밀번호가
   바뀌었음을 반증
6. **새 비밀번호로 로그인 → 성공**, `sec:authentication="name"`에 정상 표시

### 종합 검증

`./gradlew clean test` → 기존 65개 + 신규 16개(`PostImageServiceTest` 5, `UserServiceTest`
+2, `PostApiControllerTest` +4, `UserApiControllerTest` +4, `IndexControllerTest` +1) =
**81개 테스트 전부 통과**.
