# 05. API 명세

> **2026-09-09 업데이트**: 이 문서는 구현 전 시점에 작성되었다. 아래 엔드포인트는 모두 구현이
> 완료되었고, 실제 세션 쿠키·CSRF 토큰을 주고받는 curl 시나리오로 동작을 검증했다
> ([09장 4절](09-implementation-summary.md#4-실제-검증-curl-기반-e2e)). "권장" 표기가 붙은 항목 중
> 실제 반영된 것과 아닌 것(예: 상태 코드는 여전히 `Long` 200 반환, 201/204로 바꾸지 않음)을
> 각 절에 구분해 표시했다.

## 5.1 엔드포인트 목록

| 메서드 | 경로 | 설명 | 컨트롤러 메서드 | 상태 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/posts` | 게시글 등록 | `save` | ✅ 구현·검증 완료(인증 필요) |
| PUT | `/api/v1/posts/{id}` | 게시글 수정 | `update` | ✅ 구현·검증 완료(인증 필요, 작성자 권한 검증은 P2) |
| DELETE | `/api/v1/posts/{id}` | 게시글 삭제 | `delete` | ✅ 구현·검증 완료(인증 필요, 작성자 권한 검증은 P2) |
| GET | `/api/v1/posts/{id}` | 게시글 단건 조회 | `findById` | ✅ 구현·검증 완료(공개) |
| GET | `/api/v1/posts` | 게시글 목록 조회 | `findAll` | ✅ 구현·검증 완료(공개). **2026-09-09**: `/api/v1/posts/list`에서 경로 변경(P2-11) |
| POST | `/api/v1/users` | 회원가입 | `signUp` | ✅ 구현·검증 완료(공개) — 이번 구현에서 신규 추가 |
| POST | `/api/v1/posts/{postId}/comments` | 댓글 등록 | `CommentApiController.save` | ✅ 구현·검증 완료(인증 필요). **2026-09-10 신규(P3)** |
| GET | `/api/v1/posts/{postId}/comments` | 댓글 목록 조회 | `CommentApiController.findByPostId` | ✅ 구현·검증 완료(공개). **2026-09-10 신규(P3)** |
| PUT | `/api/v1/comments/{id}` | 댓글 수정 | `CommentApiController.update` | ✅ 구현·검증 완료(인증+소유자). **2026-09-10 신규(P3)**, UI 미노출(API만) |
| DELETE | `/api/v1/comments/{id}` | 댓글 삭제 | `CommentApiController.delete` | ✅ 구현·검증 완료(인증+소유자). **2026-09-10 신규(P3)** |
| POST | `/api/v1/posts/images` | 게시글 사진 업로드(multipart) | `PostApiController.uploadImage` | ✅ 구현·검증 완료(인증 필요). **2026-09-10 신규(P3)** |
| PUT | `/api/v1/users/me/password` | 비밀번호 변경 | `UserApiController.changePassword` | ✅ 구현·검증 완료(인증 필요, 현재 비밀번호 확인). **2026-09-10 신규(P3)** |

`UserApiController`에는 회원가입 + 비밀번호 변경 엔드포인트가 있다(로그인은 Spring Security의
`formLogin`이 `POST /login`으로 처리하므로 별도 API를 만들지 않았다). 댓글 API와 사진 업로드
API는 `SecurityConfig`를 **전혀 수정하지 않고도** 기존 매처(`GET /api/v1/posts/**` permitAll,
`/api/v1/**` authenticated)에 자동으로 커버된다. `/api/v1/users/me/password`도 마찬가지로
`/api/v1/users`(와일드카드 없는 정확한 경로 매치) permitAll 규칙에는 걸리지 않고
`/api/v1/**` authenticated로 떨어진다 — 상세 근거는
[04장 4.8절](04-architecture-and-layers.md#48-댓글comment-계층--구현-완료-2026-09-10),
[08장 8.14절](08-issues-and-todo.md#814-추가-구현-p3-5-게시글-사진-업로드--p3-9-비밀번호-변경-화면-2026-09-10) 참고.

## 5.2 [해결됨] 클래스명 / 파일명 불일치

```
파일: src/main/java/com/kraft/web/api/PostApiController.java
클래스: public class PostsApiController   ← 수정 전
```

```
error: class PostsApiController is public, should be declared in a file named PostsApiController.java
```

Java 언어 명세상 public 클래스는 동일한 이름의 파일에 있어야 합니다. 클래스명을 `PostApiController`로
바꿔 다른 컨트롤러(`UserApiController`)와 명명 규칙을 통일했고, 필드명도 `postService`로 정리했다.
✅ `./gradlew compileJava` 성공으로 확인.

## 5.3 엔드포인트 상세

### 5.3.1 게시글 등록

```
POST /api/v1/posts
Content-Type: application/json; charset=utf-8
```

**요청 본문** (구현 완료, `PostSaveRequestDto` record) — `author`는 DTO에서 제거했고,
`index.js`도 더 이상 이 값을 전송하지 않는다. 작성자는 서버가 `Authentication.getName()`(로그인 이메일)에서 결정한다.

```json
{
  "title": "제목",
  "content": "내용",
  "picture": null
}
```

| 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `title` | string | O | `@NotBlank` |
| `content` | string | O | `@NotBlank` |
| `picture` | string | X | nullable (실제 파일 업로드 연동은 P3) |

**응답** (실제 curl 검증 결과)

```
200 OK
Content-Type: application/json

1
```

생성된 게시글 ID(`Long`)를 그대로 반환한다. `201 Created` + `Location` 헤더로 바꾸는 것은
REST 규약상 더 적합하지만 이번 범위에서는 원래 시그니처(`Long` 반환)를 유지했다(P2 후보).

**실제 구현** (`PostApiController.java`):

```java
@PostMapping("/api/v1/posts")
public Long save(@Valid @RequestBody PostSaveRequestDto requestDto, Authentication authentication) {
    return postService.save(authentication.getName(), requestDto);
}
```

`@Valid`를 추가해 검증이 실제로 실행됨을 확인했다(빈 `title`로 요청 시 `400 Bad Request`).
로그인하지 않은 상태로 호출하면 CSRF 토큰이 없어 `403`, CSRF 토큰이 있어도 미인증이면
`SecurityConfig`의 `authenticated()` 규칙에 의해 로그인 페이지로 리다이렉트(`302`)된다
([09장 4절](09-implementation-summary.md#4-실제-검증-curl-기반-e2e) 참고).

### 5.3.2 게시글 수정 — ✅ 구현·검증 완료

```
PUT /api/v1/posts/{id}
Content-Type: application/json; charset=utf-8
```

```json
{
  "title": "수정된 제목",
  "content": "수정된 내용"
}
```

**응답**: 수정된 게시글 ID(`Long`). curl로 수정 후 `GET /api/v1/posts/{id}`에서 반영 확인 완료.

**✅ 해결됨(2026-09-09) — 작성자 권한 검증**: 기획상 작성자 본인 또는 `ADMIN`만 수정할 수 있어야
하며, 실제로 `PostApiController.update()`가 `Authentication`을 받아 `PostService.update(id, requestDto, authentication)`로
전달하고, 서비스가 작성자 본인 또는 `ROLE_ADMIN`이 아니면 `AccessDeniedException`을 던져 `403`을
반환하도록 구현했다. 타인 계정으로 로그인해 다른 사용자의 글을 PUT 시도 시 `403`, 원본 데이터가
그대로 유지됨을 curl로 확인했다([08장 8.5절](08-issues-and-todo.md#85-추가-구현-p2-4-작성자-권한-검증-2026-09-09) 참고).

### 5.3.3 게시글 삭제 — ✅ 구현·검증 완료

```
DELETE /api/v1/posts/{id}
```

**응답**: 삭제된 게시글 ID(`Long`). curl로 삭제 후 목록에서 사라짐을 확인했다.
REST 규약상 `204 No Content`가 더 적합하지만 이번 범위에서는 유지했다(P2). 수정과 동일하게
**✅ 작성자 권한 검증이 적용되어 있다**(타인 글 DELETE 시도 → `403` 확인).

### 5.3.4 게시글 단건 조회 — ✅ 구현·검증 완료

```
GET /api/v1/posts/{id}
```

**실제 응답** (`PostResponseDto` record, curl로 확인):

```json
{
  "id": 1,
  "title": "수정된 제목",
  "content": "수정된 내용",
  "picture": null,
  "author": "tester"
}
```

`createdAt`/`updatedAt`은 응답에 포함하지 않는다(목록 응답의 `modifiedDate`로 충분하다고 판단).
필요하면 `PostResponseDto`에 필드를 추가하면 된다.

### 5.3.5 게시글 목록 조회 — ✅ 구현·검증 완료 (2026-09-09 페이징 추가)

```
GET /api/v1/posts?page=0&size=10
```

> ✅ **2026-09-09**: 경로가 `/api/v1/posts/list` → `GET /api/v1/posts`로 변경되었다(P2-11).
> `POST /api/v1/posts`(등록)와 같은 경로를 HTTP 메서드로만 구분하는 REST 관례에 맞춘 것이다.
> 옛 경로를 호출하면 `GET /api/v1/posts/{id}`의 `{id}`에 `"list"`가 매칭되어 `Long` 변환
> 실패로 `400`(`요청 값의 형식이 올바르지 않습니다: id`)이 반환됨을 확인했다
> ([08장 8.12절](08-issues-and-todo.md#812-추가-구현-p2-1p2-6p2-7p2-9p2-11-나머지-정리-2026-09-09) 참고).

`page`(0부터 시작), `size`는 생략 가능하며 생략 시 `page=0`, `size=10`이 기본값이다
(`@PageableDefault(size = 10)`). Spring Data Web 지원(`spring-boot-starter-data-jpa`에
포함된 `spring-data-commons`가 자동 등록)이 쿼리 파라미터를 `Pageable`로 바인딩하며,
음수(`page=-1`)나 범위를 벗어난 페이지 번호도 예외 없이 안전하게 보정됨을 확인했다
([08장 8.6절](08-issues-and-todo.md#86-추가-구현-p2-10-페이징-2026-09-09) 참고).

**실제 응답** (`PostsPageResponseDto` record, curl로 확인 — 게시글 25건, `size=10` 기준):

```json
{
  "content": [
    { "id": 25, "title": "글 25", "author": "pager", "modifiedDate": "2026-09-09T22:32:44.965305" }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 25,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

`Page`를 직접 직렬화하지 않고 `content`/`page`/`size`/`totalElements`/`totalPages`/`first`/`last`
7개 필드만 담은 `PostsPageResponseDto`로 감쌌다(Spring Data가 `PageImpl` 직접 직렬화를 권장하지
않는다는 로그 경고를 피하고, API 계약을 명시적으로 고정하기 위함).

`PostRepository.findAllDesc(Pageable)`에 의해 **ID 내림차순(최신순)**으로 정렬되며, `JOIN FETCH p.user`로
N+1 없이 `author`가 채워짐을 확인했다. `Post.user`가 `@ManyToOne`(to-one)이라 `JOIN FETCH` +
페이징(`LIMIT`/`OFFSET`)이 DB 레벨에서 그대로 동작한다(컬렉션 fetch였다면 문제가 됐을 것).

위 응답 예시의 실제 요청 경로도 `/api/v1/posts`로 갱신되었다(예시 데이터 자체는 이전 curl
캡처값을 그대로 유지).

### 5.3.6 댓글 등록·조회·수정·삭제 — ✅ 구현·검증 완료 (2026-09-10 신규, P3)

```
POST   /api/v1/posts/{postId}/comments   댓글 등록 (인증 필요)
GET    /api/v1/posts/{postId}/comments   댓글 목록 조회 (공개, 오래된 순)
PUT    /api/v1/comments/{id}             댓글 수정 (작성자 본인/관리자만, UI 미제공)
DELETE /api/v1/comments/{id}             댓글 삭제 (작성자 본인/관리자만)
```

요청 본문(`CommentSaveRequestDto`/`CommentUpdateRequestDto`, record):

```json
{ "content": "댓글 내용" }
```

**실제 응답** (`CommentResponseDto` record, curl로 확인):

```json
[
  { "id": 1, "postId": 1, "content": "userA의 댓글입니다", "author": "userA", "createdAt": "2026-09-10T12:26:56.441574" }
]
```

**검증(curl E2E)**: `userA`가 게시글·댓글 작성 → 비로그인으로 `GET .../comments` 호출 시 `200`(공개
확인) → `userB`가 `DELETE /api/v1/comments/{id}` 시도 시 `403` `{"detail":"작성자 본인 또는
관리자만 수정·삭제할 수 있습니다. id=1", ...}`, 댓글이 그대로 남아있음을 재확인 → `userA` 본인이
삭제 시 `200`, 목록에서 사라짐 → `GET /posts/update/{postId}` 화면에 댓글이 정확히 반영됨(삭제된
댓글 미노출)까지 전부 확인했다. 상세는 [09장](09-implementation-summary.md) 참고.

### 5.3.7 게시글 사진 업로드 — ✅ 구현·검증 완료 (2026-09-10 신규, P3)

```
POST /api/v1/posts/images
Content-Type: multipart/form-data; boundary=...
(필드명 "file")
```

허용 확장자: jpg/jpeg/png/gif/webp, 최대 5MB. **실제 응답**(`ImageUploadResponseDto` record):

```json
{ "url": "/images/cf59db8f-659f-446e-bc2b-13b4071f6a5a.png" }
```

이 `url`을 `PostSaveRequestDto.picture`에 그대로 넣어 게시글을 등록한다(2단계 흐름 —
[06장](06-view-and-templates.md) 참고).

**검증(curl E2E)**: CSRF 없이 업로드 → `403` / CSRF만 있고 미인증 → `302` / 로그인 후 정상
PNG 업로드 → `200`+URL, 반환된 URL을 직접 GET하면 `200`과 원본 파일 크기 그대로 확인 /
`.txt` 확장자 업로드 → `400` `{"detail":"허용되지 않는 파일 형식입니다: txt", ...}` / 업로드
URL로 게시글 등록 후 단건 조회 응답에 `picture` 필드 정상 반영 / `GET /posts/update/{id}`에
`<img>` 태그로 정확히 렌더링됨을 확인했다.

### 5.3.8 비밀번호 변경 — ✅ 구현·검증 완료 (2026-09-10 신규, P3)

```
PUT /api/v1/users/me/password
Content-Type: application/json; charset=utf-8

{ "currentPassword": "old12345", "newPassword": "new12345" }
```

**응답**: 성공 시 `204 No Content`(본문 없음 — 이 프로젝트의 다른 API가 대부분 `Long` ID를
반환하는 것과 다른 규약이다. 반환할 자연스러운 ID가 없는 요청이라 REST 관례에 맞춰 의도적으로
다르게 설계했다).

**검증(curl E2E)**: 미인증+CSRF만 → `302`(로그인 페이지) / 현재 비밀번호 틀림 → `400`
`{"detail":"현재 비밀번호가 일치하지 않습니다.", ...}` / 올바른 현재 비밀번호로 변경 → `204` →
**옛 비밀번호로 로그인 시도 시 실제로 실패**(`/login?error`)하고 **새 비밀번호로는 로그인
성공**함을 확인해, 응답 코드만이 아니라 비밀번호가 실제로 바뀌었는지까지 검증했다.

### 5.3.9 이메일 인증 — ✅ 구현·검증 완료 (2026-09-10 신규, P3)

트리거는 회원가입 성공 직후 서버 내부에서 자동으로 일어나며, 별도의 발송 요청 API는 없다.

```
POST /api/v1/users   (기존 회원가입 API)
→ 성공 시 서버가 내부적으로 인증 메일을 발송한다(응답 자체는 기존과 동일하게 Long ID).
```

인증 완료는 JSON API가 아니라 **화면 라우트**다(이메일 클릭용 링크이므로 브라우저 GET 이동을
전제로 함):

```
GET /users/verify?token={uuid}
```

**응답**: 항상 `200` + `user/verify-result` 뷰. 토큰이 유효하면 승격 완료 메시지, 무효(존재하지
않음/이미 사용됨/만료됨)면 사유 메시지를 화면에 담아 렌더링한다 — HTTP 상태 코드로 성공/실패를
구분하지 않고 화면 텍스트로 구분하는 방식이다(사용자가 클릭한 링크가 깨진 것처럼 보이지 않도록
항상 200으로 응답).

**검증(curl E2E, `bootRun` + `local` 프로파일, `ConsoleEmailSender`가 콘솔에 남긴 링크 사용)**:
- 회원가입 → 콘솔 로그에 `http://localhost:8080/users/verify?token=<uuid>` 링크 출력 확인
- 해당 토큰으로 `GET /users/verify` → `200` + 성공 메시지, Hibernate 로그에
  `update users set ... role=?` 실행 확인(실제 DB 반영)
- **같은 토큰 재요청** → `200`이지만 실패 메시지("유효하지 않은 인증 링크입니다.") — 1회용 확인
- 존재하지 않는 임의 토큰 → 동일한 실패 메시지

## 5.4 예외 응답 — ✅ 해결됨 (2026-09-09, `ApiExceptionHandler`)

`web.api` 패키지의 REST 컨트롤러에 한정된 `@RestControllerAdvice`(`ApiExceptionHandler`)가
Spring Boot 4의 `ProblemDetail`(RFC 9457)로 오류를 통일한다. 자세한 코드와 설계 결정은
[04장 4.6절](04-architecture-and-layers.md#46-예외-처리-전략--해결됨2026-09-09-추가-구현),
curl 검증 전/후 비교표는 [08장 8.7절](08-issues-and-todo.md#87-추가-구현-p1-12-전역-예외-처리기-2026-09-09) 참고.

| 상황 | 현재 | 응답 |
| --- | --- | --- |
| 존재하지 않는 ID 조회 | ✅ 해결 | `400` `{"detail":"해당 게시글이 없습니다. id=999", "status":400, "title":"Bad Request", ...}` |
| 경로 변수 타입 불일치(예: `/api/v1/posts/{id}`에 숫자가 아닌 값) | ✅ 해결(2026-09-09 추가) | `400` `{"detail":"요청 값의 형식이 올바르지 않습니다: id", ...}` — P2-11 경로 변경 검증 중 발견 |
| 잘못된 JSON 본문 | ✅ 해결 | `400` `{"detail":"요청 본문을 읽을 수 없습니다.", ...}` |
| 검증 실패(`@NotBlank` 등) | ✅ 해결 | `400` `{"detail":"title: 제목은 필수입니다.", ...}` |
| 이메일 중복 가입 | ✅ 해결 | `400` `{"detail":"이미 가입된 이메일입니다. email=...", ...}` |
| 타인 게시글 수정/삭제 | ✅ 해결 ([05장 5.3.2절](#532-게시글-수정--구현검증-완료), P2-4) | `403` `{"detail":"작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=1", "status":403, "title":"Forbidden", ...}` |
| 미인증 상태의 쓰기 요청 | CSRF 토큰 없으면 `403`, 토큰이 있어도 미인증이면 로그인 페이지로 `302` 리다이렉트 | `401`이 아닌 `403`/`302` (`formLogin` 기본 엔트리포인트가 전역 적용되어 JSON API에도 리다이렉트가 발생함 — 미해결, P2 후보) |
| 그 외 예상치 못한 예외 | ✅ 해결(catch-all) | `500` `{"detail":"서버 내부 오류가 발생했습니다.", ...}` — 원인은 서버 로그에만 기록, 클라이언트에 노출 안 함 |
| 화면 컨트롤러(`IndexController`)의 오류 | 의도적으로 범위 밖 | 여전히 `500`(Spring Boot 기본), 변경 없음 |

## 5.5 CSRF — ✅ 해결됨 (실제 curl로 검증 완료)

`spring-boot-starter-security`는 CSRF 보호를 **기본 활성화**한다. 아래는 문제 발견 당시 서술이며,
현재는 해결되어 실제로 정상 동작함을 확인했다.

> `index.js`의 jQuery AJAX는 CSRF 토큰을 전혀 보내지 않으므로 현재 상태로는 POST/PUT/DELETE 요청이
> **403 Forbidden**으로 거부됩니다.

**실제 반영**: `layout/header.html`에 메타 태그를 추가하고,

```html
<meta name="_csrf" th:content="${_csrf.token}"/>
<meta name="_csrf_header" th:content="${_csrf.headerName}"/>
```

`index.js`에서 전역 AJAX 설정으로 헤더를 주입한다(실제 코드, `$(document).ready` 안에서 `main.init()`과 함께 등록).

```javascript
$(function () {
    var csrfToken = $('meta[name="_csrf"]').attr('content');
    var csrfHeader = $('meta[name="_csrf_header"]').attr('content');

    $(document).ajaxSend(function (e, xhr) {
        if (csrfHeader) {
            xhr.setRequestHeader(csrfHeader, csrfToken);
        }
    });

    main.init();
});
```

curl로 세션 쿠키 + CSRF 토큰을 함께 보내면 `200`, 토큰 없이 보내면 `403`이 되는 것을 직접 확인했다
([09장 4절](09-implementation-summary.md#4-실제-검증-curl-기반-e2e)). `/h2-console/**`만
`csrf().ignoringRequestMatchers("/h2-console/**")`로 예외 처리했다.

자세한 보안 설정은 [07. 설정과 실행](07-configuration.md)을 참고하세요.

## 5.6 클라이언트 스크립트 분석 (`static/js/app/index.js`) — ✅ 수정 완료

```javascript
var main = {
    init: function () { /* #btn-save, #btn-update, #btn-delete 클릭 바인딩 */ },
    save: function () { /* POST /api/v1/posts */ },
    update: function () { /* PUT /api/v1/posts/{id} */ },
    delete: function () { /* DELETE /api/v1/posts/{id} */ }
};
main.init();
```

위는 문제 발견 당시 구조다. 아래는 실제 반영 후 평가다.

| 항목 | 평가 |
| --- | --- |
| 객체 리터럴 + `init()` 패턴 | 전역 오염을 줄이는 구조로 적절합니다. 유지. |
| `var` 사용 | Bootstrap 4/jQuery 3 조합의 관례이나, `const`/`let`으로 현대화 가능합니다(P2, 이번 범위 미적용). |
| `delete`를 메서드명으로 사용 | ES5 예약어와 겹치지만 속성명으로는 유효합니다. 그대로 유지했습니다. |
| `dataType: 'json'` | 서버가 `Long`(예: `1`)을 그대로 반환하므로 유효한 JSON으로 파싱됨을 확인했습니다. |
| `alert()` 기반 피드백 | 학습용으로 무방하나 실서비스에서는 토스트/인라인 메시지 권장(P2). |
| CSRF 토큰 미전송 | ✅ **해결** — 5.5절의 `ajaxSend` 헤더 주입 반영, curl로 검증 완료. |
| `author` 전송 | ✅ **해결** — `save()`에서 `author` 필드를 제거했다(5.3.1절 참고). |
| `main.init()` 호출 시점 | ✅ **개선** — `$(document).ready` 안으로 이동해 CSRF 메타 태그 렌더링 이후 바인딩되도록 보장. |
| `#btn-*` 요소 부재 시 | jQuery는 조용히 무시하므로 오류가 나지 않습니다. 페이지별 스크립트 분리는 이번 범위에서 적용하지 않았습니다(P2). |
