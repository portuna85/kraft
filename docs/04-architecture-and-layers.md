# 04. 아키텍처와 계층

> **2026-09-09 업데이트**: 이 문서에서 지적한 미구현 항목(PostService 5개 메서드, DTO 4종, 예외 처리 부재,
> UserService/IndexController)은 P0+P1 구현으로 대부분 해결되었다. 단, DTO는 이 문서의 초기 제안과 달리
> **Lombok 클래스가 아닌 Java record**로 구현했다(사용자 요청 반영, 근거는
> [09장 3절](09-implementation-summary.md#3-dto를-record로-전환한-결정)). 아래 각 절에 해결 여부와 실제
> 구현 코드를 반영했다. 예외 처리(4.6절)는 최초에는 P2로 제외했으나 2026-09-09에 추가 구현·검증했다.

## 4.1 계층 구조

프로젝트는 전형적인 3계층 아키텍처를 패키지로 표현하고 있습니다.

```
[브라우저]
    │  화면 요청 (GET /, /posts/save …)          │  AJAX (POST/PUT/DELETE /api/v1/posts)
    ▼                                            ▼
┌──────────────────────┐              ┌──────────────────────────┐
│  web.IndexController │              │  web.api.*ApiController  │
│  (@Controller)       │              │  (@RestController)       │
└──────────┬───────────┘              └────────────┬─────────────┘
           │                                       │
           │            web.dto.*  (요청/응답 DTO) │
           └───────────────────┬───────────────────┘
                               ▼
                   ┌───────────────────────┐
                   │  service.*Service     │   트랜잭션 경계
                   │  (@Service)           │
                   └───────────┬───────────┘
                               ▼
                   ┌───────────────────────┐
                   │  domain.*Repository   │
                   │  (JpaRepository)      │
                   └───────────┬───────────┘
                               ▼
                   ┌───────────────────────┐
                   │  domain.* (Entity)    │  ← 비즈니스 규칙의 소재지
                   └───────────────────────┘
```

`domain` 패키지가 엔티티와 리포지토리를 함께 담는 구조는
"엔티티에 비즈니스 로직을 두고 서비스는 트랜잭션과 조립만 담당한다"는 도메인 주도 접근에 부합합니다.
현재 엔티티에 비즈니스 메서드가 없어 그 의도가 실현되지 않았을 뿐, 패키지 배치 자체는 적절합니다.

## 4.2 계층별 책임과 현재 상태

| 계층 | 책임 | 현재 |
| --- | --- | --- |
| Controller | 요청 바인딩, 검증 트리거, 응답 변환 | `PostsApiController`만 시그니처 존재 |
| Service | 트랜잭션 경계, 유스케이스 조립, 엔티티 조회/위임 | **전부 미구현** |
| Repository | 데이터 접근 | 최소 구현 |
| Entity | 상태와 불변식, 비즈니스 메서드 | 필드만 존재 |
| DTO | 계층 간 데이터 전달, 검증 규칙 | **빈 클래스 4개** |

## 4.3 [해결됨] PostService 미구현

> ✅ 아래 5개 메서드 모두 실제로 구현되었고 curl E2E로 등록/조회/수정/삭제를 검증했다
> ([09장 4절](09-implementation-summary.md#4-실제-검증-curl-기반-e2e)).

`PostsApiController`는 다음 5개 메서드를 호출하지만 `PostService`에는 필드 하나뿐입니다.

```java
@RequiredArgsConstructor
@Service
public class PostService {
    private final PostRepository postRepository;
    // save / update / delete / findById / findAllDesc 전부 없음
}
```

실제 컴파일 오류:

```
PostApiController.java:21: error: cannot find symbol   symbol: method save(PostSaveRequestDto)
PostApiController.java:26: error: cannot find symbol   symbol: method update(Long,PostUpdateRequestDto)
PostApiController.java:31: error: cannot find symbol   symbol: method delete(Long)
PostApiController.java:37: error: cannot find symbol   symbol: method findById(Long)
PostApiController.java:42: error: cannot find symbol   symbol: method findAllDesc()
```

### 실제 구현 (`src/main/java/com/kraft/service/post/PostService.java`)

- **클래스 레벨 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional` 재지정**
  조회 전용 트랜잭션은 Hibernate가 flush를 생략하고 스냅샷을 만들지 않아 성능 이점이 있습니다.
- **수정은 `save()` 재호출이 아니라 변경 감지로 처리**
  트랜잭션 안에서 엔티티를 조회해 비즈니스 메서드(`post.update(...)`)를 호출하면 커밋 시 UPDATE가 나갑니다.
- **엔티티를 컨트롤러로 반환하지 않고 DTO(record)로 변환**

```java
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long save(String email, PostSaveRequestDto requestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + email));
        return postRepository.save(requestDto.toEntity(user)).getId();
    }

    @Transactional
    public Long update(Long id, PostUpdateRequestDto requestDto, Authentication authentication) {
        Post post = findPost(id);
        validateOwner(post, authentication);
        post.update(requestDto.title(), requestDto.content());  // record accessor, 변경 감지
        return id;
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        Post post = findPost(id);
        validateOwner(post, authentication);
        postRepository.delete(post);
    }

    // 작성자 본인 또는 ROLE_ADMIN만 통과, 아니면 AccessDeniedException → Security가 자동으로 403 처리
    private void validateOwner(Post post, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getKey()));
        boolean isOwner = post.getUser() != null
                && post.getUser().getEmail().equals(authentication.getName());
        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=" + post.getId());
        }
    }

    public PostResponseDto findById(Long id) {
        return new PostResponseDto(findPost(id));
    }

    public PostsPageResponseDto findAllDesc(Pageable pageable) {
        Page<PostsListResponseDto> page = postRepository.findAllDesc(pageable)
                .map(PostsListResponseDto::new);
        return new PostsPageResponseDto(page);
    }

    private Post findPost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다. id=" + id));
    }
}
```

`.toList()`는 Java 16+에서 사용 가능하며, 이 프로젝트는 Java 25이므로 `Collectors.toList()`보다 간결합니다.
`email`은 `PostApiController.save()`가 `Authentication.getName()`에서 얻어 전달한다(로그인 사용자 = 작성자,
[05장 5.3.1절](05-api-spec.md#531-게시글-등록) 참고).

**P2-10 (페이징) — ✅ 2026-09-09 추가 구현 완료.** `findAllDesc()`는 더 이상 전체 목록을
`List`로 반환하지 않고 `Pageable`을 받아 `PostsPageResponseDto`(record)로 감싼 페이지 단위
결과를 반환한다. 상세 근거와 curl 검증 결과는 [08장 8.6절](08-issues-and-todo.md#86-추가-구현-p2-10-페이징-2026-09-09) 참고.

**P2-4 (작성자 권한 검증) — ✅ 2026-09-09 추가 구현 완료.** `update`/`delete`도 같은 방식으로
`Authentication`을 받아 `validateOwner()`로 작성자 본인 또는 `ROLE_ADMIN`인지 검사한다. 통과하지
못하면 Spring Security 표준 예외인 `AccessDeniedException`을 던지는데, 별도 `@RestControllerAdvice`
없이도 `SecurityFilterChain`에 기본 포함된 `ExceptionTranslationFilter`가 이를 가로채 자동으로
`403`을 반환한다. 상세 검증 결과는 [08장 8.5절](08-issues-and-todo.md#85-추가-구현-p2-4-작성자-권한-검증-2026-09-09) 참고.

## 4.4 [해결됨] DTO 4종이 빈 클래스 → record로 구현

```java
public class PostSaveRequestDto { }
public class PostUpdateRequestDto { }
public class PostResponseDto { }
public class PostsListResponseDto { }
```

위는 문제 발견 당시 상태다. DTO에 필드가 없어 요청 본문이 바인딩되지 않고 응답이 `{}`가 되는 문제가
있었다. `spring-boot-starter-validation`이 이미 의존성에 있어 **검증 애노테이션을 DTO에 붙이는 것**이
정석이라는 원래 판단은 유지하되, **구현체는 Lombok 클래스가 아니라 Java record**로 작성했다
(사용자 요청, 근거는 [09장 3절](09-implementation-summary.md#3-dto를-record로-전환한-결정)). 아래
4.4.1~4.4.3은 실제 반영된 코드다.

### 4.4.1 PostSaveRequestDto — 구현 완료 (record)

`static/js/app/index.js`의 `save()`가 원래 보내던 필드는 `title`, `author`, `content` 3개였습니다.
그러나 도메인에는 `author`가 없고 `user` 연관관계가 있습니다.
**작성자는 클라이언트가 보낸 값이 아니라 인증 주체(세션)에서 가져와야 합니다.**
클라이언트가 `author`를 마음대로 지정하면 타인 명의로 글을 쓸 수 있으므로, DTO에서
`author` 필드를 제거하고 `index.js`도 이 값을 더 이상 전송하지 않도록 함께 수정했다
([06장](06-view-and-templates.md), [09장](09-implementation-summary.md) 참고).

```java
public record PostSaveRequestDto(

        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content,

        String picture
) {

    public Post toEntity(User user) {
        return Post.builder()
                .title(title)
                .content(content)
                .picture(picture)
                .user(user)
                .build();
    }
}
```

### 4.4.2 PostUpdateRequestDto — 구현 완료 (record)

```java
public record PostUpdateRequestDto(

        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content
) {
}
```

### 4.4.3 PostResponseDto / PostsListResponseDto — 구현 완료 (record)

**엔티티를 받는 보조 생성자를 record에 추가하는 형태**로 구현했다. record는 canonical 생성자
외의 추가 생성자를 정의할 수 있고, `this(...)`로 canonical 생성자에 위임하면 된다.

```java
public record PostResponseDto(
        Long id,
        String title,
        String content,
        String picture,
        String author
) {

    public PostResponseDto(Post entity) {
        this(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getPicture(),
                entity.getUser() != null ? entity.getUser().getName() : null
        );
    }
}
```

```java
public record PostsListResponseDto(
        Long id,
        String title,
        String author,
        LocalDateTime modifiedDate
) {

    public PostsListResponseDto(Post entity) {
        this(
                entity.getId(),
                entity.getTitle(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                entity.getUpdatedAt()
        );
    }
}
```

`PostsListResponseDto`는 목록에 필요한 4개 필드만 담아 응답 크기를 줄인다. 필드명은
`index.html`의 `{{modifiedDate}}` 표기에 맞춰 **`modifiedDate`**로 통일했고, 값은
`BaseEntity.updatedAt`에서 가져온다.

record의 접근자는 `id()`, `title()`처럼 Lombok `@Getter`의 `getId()`와 이름이 다르지만,
Thymeleaf/SpringEL의 프로퍼티 표현식(`${post.title}`)은 Spring Framework 6.1+부터 record
accessor를 인식하므로 템플릿에서는 문법 차이가 드러나지 않는다
([09장 3절](09-implementation-summary.md#3-dto를-record로-전환한-결정) 참고). 자바 코드에서
직접 호출할 때만 `getX()` 대신 `x()`를 쓰면 된다(예: `PostService.update()`의 `requestDto.title()`).

## 4.5 DTO를 두는 이유 (엔티티 직접 노출 금지)

이 프로젝트에서 엔티티를 컨트롤러 응답으로 반환하면 안 되는 구체적 이유입니다.

1. **비밀번호 노출**: `User`에 `@Getter`가 있어 `password` 해시가 JSON에 직렬화됩니다.
2. **지연 로딩 예외**: `Post.user`를 `LAZY`로 바꾸면 트랜잭션 종료 후 Jackson 직렬화 시점에
   `LazyInitializationException` 또는 프록시 직렬화 오류가 발생합니다.
3. **순환 참조**: `User.posts` ↔ `Post.user` 양방향 관계를 그대로 직렬화하면 무한 재귀에 빠집니다.
4. **API 계약과 스키마의 결합**: 컬럼 변경이 곧 API 파괴 변경이 됩니다.

## 4.6 예외 처리 전략 — ✅ 해결(2026-09-09 추가 구현)

> 초기 P0+P1 구현에서는 `@RestControllerAdvice`를 추가하지 않아 `IllegalArgumentException`이
> 500으로 그대로 노출되었다. 이후 아래 내용대로 `ApiExceptionHandler`를 추가해 해결했다
> ([08장 8.7절](08-issues-and-todo.md#87-추가-구현-p1-12-전역-예외-처리기-2026-09-09) 참고).

**실제 구현** (`web/exception/ApiExceptionHandler.java`) — 애초 제안(`ResponseEntity<ErrorResponse>`)
대신 **Spring Boot 4가 지원하는 `ProblemDetail`(RFC 9457)을 직접 반환**하는 방식을 택했다.
컨트롤러/`@ExceptionHandler` 메서드가 `ProblemDetail`을 반환하면 프레임워크가 상태 코드와
`Content-Type: application/problem+json`을 자동으로 설정해주므로, 커스텀 `ErrorResponse`
클래스나 `ResponseEntity` 래핑이 필요 없다.

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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "요청 값의 형식이 올바르지 않습니다: " + e.getName());
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

**당초 제안과 다른 점 3가지**:

1. `basePackages = "com.kraft.web.api"`로 **REST 컨트롤러에만 범위를 한정**했다. 전역으로
   두면 `IndexController`(화면 컨트롤러)의 예외까지 JSON `ProblemDetail`로 바뀌는데, 브라우저용
   Thymeleaf 뷰 요청에는 부적절하다. `GET /posts/update/999`(존재하지 않는 글)가 여전히 이
   범위 밖에서 `500`(변경 없음)을 반환함을 확인했다.
2. `AccessDeniedException`도 함께 처리해 [4.3절](#43-해결됨-postservice-미구현)에서 구현한
   작성자 권한 검증(`403`)의 응답 형식을 다른 오류와 통일했다. catch-all `Exception` 핸들러가
   있으므로 이 핸들러가 없으면 `403`이 `500`으로 잘못 바뀔 뻔했다 — 구현 순서에 주의가 필요했다.
3. `HttpMessageNotReadableException`(잘못된 JSON 본문)도 추가해 400으로 처리한다.
4. **(2026-09-09 추가)** `MethodArgumentTypeMismatchException`도 400으로 처리한다.
   `/api/v1/posts/list`(P2-11로 제거된 옛 목록 경로)를 호출하면 `GET /api/v1/posts/{id}`의
   `{id}`에 문자열 `"list"`가 매칭되어 `Long` 변환에 실패하는데, 이 핸들러가 없으면 catch-all에
   잡혀 `500`이 되는 것을 curl로 실제 확인한 뒤 추가했다. 상세는
   [08장 8.12절](08-issues-and-todo.md#812-추가-구현-p2-1p2-6p2-7p2-9p2-11-나머지-정리-2026-09-09) 참고.

curl로 확인한 실제 응답 예시와 전/후 비교표는 [08장 8.7절](08-issues-and-todo.md#87-추가-구현-p1-12-전역-예외-처리기-2026-09-09) 참고.

## 4.7 UserService / UserApiController / IndexController — ✅ 구현 완료

세 클래스 모두 본문이 비어 있었다. 기획 주석을 기준으로 필요한 유스케이스를 아래와 같이 구현했다.

### UserService

| 유스케이스 | 설명 | 상태 |
| --- | --- | --- |
| `signUp` | 이메일 중복 검사 → 비밀번호 암호화 → `Role.GUEST`로 저장 | ✅ 구현, curl로 검증 완료 |
| `promoteToUser` | `Role.USER`로 승격하는 메서드만 구현 | ✅ 서비스 메서드만 존재, 컨트롤러 미노출 |
| `changePassword` | 새 비밀번호 암호화·변경 | ✅ 서비스 메서드 구현, 컨트롤러 미노출(P2) |
| 실제 이메일 인증 토큰 발급/발송 | 메일 발송, 토큰 검증 | 미해결(P3) — `promoteToUser`를 실제로 호출하는 흐름이 없음 |

> 비밀번호 암호화는 `spring-boot-starter-security`가 제공하는 `PasswordEncoder`
> (`PasswordEncoderFactories.createDelegatingPasswordEncoder()`)로 처리한다. `SecurityConfig`에
> 빈으로 등록했다.

### UserApiController — ✅ 구현 완료

`POST /api/v1/users` 회원가입 엔드포인트 1개만 노출했다(비밀번호 변경·프로필 조회 API는 대응하는
화면이 없어 이번 범위에서 제외, P2). 요청 본문 검증에는 `SignUpRequestDto`(record, `@Email`/`@NotBlank`/`@Size`)를 사용한다.

### IndexController — ✅ 구현 완료

`index.html`, `post-save.html`, `post-update.html` 세 화면에 대응하는 매핑을 구현했다. 아래는
실제 반영된 코드 그대로다.

```java
@RequiredArgsConstructor
@Controller
public class IndexController {

    private final PostService postService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("posts", postService.findAllDesc());
        return "index";
    }

    @GetMapping("/posts/save")
    public String postsSave() { return "post/post-save"; }

    @GetMapping("/posts/update/{id}")
    public String postsUpdate(@PathVariable Long id, Model model) {
        model.addAttribute("post", postService.findById(id));
        return "post/post-update";
    }
}
```

로그인 사용자명 표시(`{{userName}}`)는 `@AuthenticationPrincipal` 또는
템플릿의 `sec:authentication="name"`으로 처리할 수 있습니다
([06. 뷰와 템플릿](06-view-and-templates.md) 참고).

## 4.8 댓글(Comment) 계층 — ✅ 구현 완료 (2026-09-10)

`Post`와 동일한 계층 구조(Repository → Service → Controller)를 그대로 따랐다. 새로 생긴
`GET /signup` 라우팅도 `IndexController`에 추가해 화면 라우팅을 한 컨트롤러가 전담하는 기존
구조를 유지했다(`CommentService` 주입 추가).

### `com.kraft.service.support.OwnershipPolicy` — 소유권 검증 공유 유틸

`PostService.validateOwner()`([4.3절](#43-해결됨-postservice-미구현))와 완전히 동일한 로직이
`CommentService`에도 필요했다. `User.java` 클래스 주석에 "일반사용자는 Post, Comment의 작성,
수정, 삭제가 가능하다"고 **두 도메인을 나란히 명시**하고 있어 우연의 일치가 아닌 진짜 공유
정책이라고 판단해, 복제하지 않고 `service/support/OwnershipPolicy`(순수 정적 유틸)로 추출했다.

```java
public final class OwnershipPolicy {
    public static void validateOwner(Authentication authentication, User owner, Long entityId) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.ADMIN.getKey()));
        boolean isOwner = owner != null && owner.getEmail().equals(authentication.getName());
        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=" + entityId);
        }
    }
}
```

`PostService.validateOwner()`는 1줄 위임으로 축소했다(메시지 문자열 동일 유지):
```java
private void validateOwner(Post post, Authentication authentication) {
    OwnershipPolicy.validateOwner(authentication, post.getUser(), post.getId());
}
```

**리팩터링 전 안전성 확인**: `PostServiceTest`는 `AccessDeniedException`을 `.isInstanceOf(...)`로만
검증하고 메시지 문자열은 확인하지 않으며, `PostApiControllerTest`는 `PostService`를
`@MockitoBean`으로 완전히 모킹해 내부 구현 변경에 영향받지 않음을 리팩터링 전에 `grep`으로
직접 확인했다. `./gradlew clean test` 결과 기존 45개 테스트 전부 회귀 없이 통과했다.

### `CommentService`

```java
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long save(Long postId, String email, CommentSaveRequestDto requestDto) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다. id=" + postId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + email));
        return commentRepository.save(requestDto.toEntity(post, user)).getId();
    }

    @Transactional
    public Long update(Long id, CommentUpdateRequestDto requestDto, Authentication authentication) {
        Comment comment = findComment(id);
        OwnershipPolicy.validateOwner(authentication, comment.getUser(), id);
        comment.update(requestDto.content());
        return id;
    }

    @Transactional
    public void delete(Long id, Authentication authentication) { /* update와 동일 패턴 */ }

    public List<CommentResponseDto> findByPostId(Long postId) { /* JOIN FETCH 결과를 DTO로 변환 */ }
}
```

`PostService`와 마찬가지로 없는 대상 조회는 전부 `IllegalArgumentException`(→`ApiExceptionHandler`가
400 처리), 권한 없음은 `AccessDeniedException`(→403).

### DTO 3종 (`web/dto/comment/`, record) — 기존 `web/dto/post/*` 패턴 그대로

```java
public record CommentSaveRequestDto(@NotBlank(message = "내용은 필수입니다.") String content) {
    public Comment toEntity(Post post, User user) {
        return Comment.builder().content(content).post(post).user(user).build();
    }
}

public record CommentUpdateRequestDto(@NotBlank(message = "내용은 필수입니다.") String content) { }

public record CommentResponseDto(Long id, Long postId, String content, String author, LocalDateTime createdAt) {
    public CommentResponseDto(Comment entity) {
        this(entity.getId(), entity.getPost().getId(), entity.getContent(),
             entity.getUser() != null ? entity.getUser().getName() : null, entity.getCreatedAt());
    }
}
```

### `CommentApiController` — API 경로와 `SecurityConfig` 매처 커버리지

API 경로는 `web.api` 패키지(→`ApiExceptionHandler` 자동 적용)에 두었고, **기존
`SecurityConfig`를 전혀 수정하지 않고도** 의도한 권한이 정확히 적용됨을 매처 단위로 검증했다.

| 요청 | 매칭되는 기존 규칙 | 결과 |
| --- | --- | --- |
| `GET /api/v1/posts/{postId}/comments` | `GET /api/v1/posts/**` permitAll | 공개 |
| `POST /api/v1/posts/{postId}/comments` | (GET 전용 규칙 미매치) → `/api/v1/**` authenticated | 로그인 필요 |
| `PUT`/`DELETE /api/v1/comments/{id}` | `/api/v1/**` authenticated | 로그인 필요(소유권은 서비스 계층에서) |

이 표는 실제로 curl로도 재검증했다(09장 참고) — `SecurityConfig.java` 파일은 이번 작업에서
**변경하지 않았다.**

### 댓글 UI 범위 결정: 수정(edit) 기능은 API만 제공, 화면 UI는 등록/삭제만

댓글 인라인 수정은 "목록 항목을 편집 모드로 전환 → textarea 치환 → 저장/취소 토글"이 필요해
클라이언트 상태 관리가 늘어나는 데 비해, 댓글은 삭제 후 재작성으로 충분히 대체 가능한 짧은
텍스트라고 판단해 화면에서는 뺐다. `PUT /api/v1/comments/{id}`는 API로는 완성했고
`CommentApiControllerTest`로 검증했다.

댓글 삭제 버튼은 `sec:authorize="isAuthenticated()"`로 **로그인한 모든 사용자에게 노출**한다
(작성자 이메일을 `CommentResponseDto`에 담지 않아 클라이언트가 소유권을 정확히 판별할 수 없음).
실제 권한은 서버(`OwnershipPolicy` → 403)가 강제하므로, 타인 댓글 삭제를 시도하면 alert로
오류 메시지가 뜨는 정도의 UX를 허용했다 — `post-update.html`이 기존에도 수정/삭제 버튼을
소유권과 무관하게 항상 보여주는 것과 동일한 패턴이다.
