# 06. 뷰와 템플릿

> **2026-09-09 업데이트**: 이 문서에서 지적한 문제는 모두 해결되었다 — 템플릿 5개를 Thymeleaf
> 문법으로 전면 전환했고(6.1~6.2절의 "A안"을 그대로 채택), OAuth2 링크는 `/login`으로 교체했으며
> (6.3절), `post-update.html`의 `label for` 오류도 수정했다(6.4.2절). `bootRun` 후 curl로
> 실제 렌더링 결과(Mustache 잔재 없음, `label for`가 올바른 `input id`를 가리킴)를 확인했다.
> 상세 검증 결과는 [09. 구현 요약](09-implementation-summary.md) 참고.

## 6.1 [해결됨] 템플릿 엔진 불일치

### 현상 (구현 전)

| 항목 | 값 |
| --- | --- |
| 선언된 의존성 | `spring-boot-starter-thymeleaf`, `thymeleaf-extras-springsecurity6` |
| 템플릿 위치 | `src/main/resources/templates/` (Thymeleaf 기본 경로와 동일) |
| 템플릿 확장자 | `.html` (Thymeleaf 기본 확장자와 동일) |
| **템플릿 문법** | **Mustache** (`{{>layout/header}}`, `{{#posts}}`, `{{^userName}}`) |

`spring-boot-starter-mustache`는 **의존성에 없습니다.**
따라서 Thymeleaf가 이 파일들을 처리하게 되는데, Thymeleaf는 `{{...}}`를 템플릿 표현식으로 인식하지 않고
**단순 텍스트로 그대로 출력**합니다. 화면에는 `{{>layout/header}}` 같은 문자열이 그대로 노출됩니다.

또한 `{{>layout/header}}`가 텍스트로 남으면 `<!DOCTYPE html>`과 `<html>` 태그가 렌더링되지 않아
문서 구조 자체가 깨집니다.

### 선택지

| 방안 | 장점 | 단점 |
| --- | --- | --- |
| **A. 템플릿을 Thymeleaf 문법으로 변환** | 의존성 변경 없음. `thymeleaf-extras-springsecurity6`를 실제로 활용 가능. IDE 지원과 자연 템플릿 이점 | 템플릿 5개 전면 수정 |
| B. Mustache 스타터로 교체 | 템플릿 수정 불필요 | 의존성 2개(thymeleaf, extras) 제거 후 추가 필요. `sec:` 네임스페이스 사용 불가 |

**본 문서는 "기존 라이브러리를 기준으로 작성"이라는 원칙에 따라 A안을 기준으로 서술합니다.**

✅ **A안이 실제로 적용되었다.** 아래 6.2절의 가이드대로 템플릿 5개를 Thymeleaf 문법으로 전환했다.

## 6.2 Thymeleaf 전환 가이드

### 6.2.1 문법 대응표

| Mustache | Thymeleaf | 비고 |
| --- | --- | --- |
| `{{>layout/header}}` | `<div th:replace="~{layout/header :: header}">` | Thymeleaf는 fragment 단위로 삽입 |
| `{{title}}` | `[[${title}]]` 또는 `th:text="${title}"` | |
| `{{#posts}} … {{/posts}}` | `th:each="post : ${posts}"` | 반복 |
| `{{#userName}} … {{/userName}}` | `th:if="${userName}"` | 존재 시 출력 |
| `{{^userName}} … {{/userName}}` | `th:unless="${userName}"` | 부재 시 출력 |
| `{{post.title}}` | `${post.title}` | |

### 6.2.2 레이아웃 처리

현재 `header.html`은 `<body>` 여는 태그까지, `footer.html`은 `</body></html>` 닫는 태그를 담는
**"반쪽 HTML" 방식**입니다. Mustache의 단순 텍스트 include에서는 가능하지만
Thymeleaf는 **완결된 마크업 조각(fragment) 단위**로 동작하므로 이 구조를 그대로 옮길 수 없습니다.

두 가지 접근이 가능합니다.

**(1) fragment 분리 방식** — 추가 라이브러리 없이 Thymeleaf 표준 기능만 사용합니다.

`layout/header.html`:

```html
<head th:fragment="head">
    <title>스프링부트 웹서비스</title>
    <meta charset="UTF-8">
    <meta name="_csrf" th:content="${_csrf.token}">
    <meta name="_csrf_header" th:content="${_csrf.headerName}">
    <link rel="stylesheet"
          href="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css">
</head>
```

`layout/footer.html`:

```html
<div th:fragment="scripts">
    <script src="https://code.jquery.com/jquery-3.3.1.min.js"></script>
    <script src="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/js/bootstrap.min.js"></script>
    <script src="/js/app/index.js"></script>
</div>
```

각 페이지:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head th:replace="~{layout/header :: head}"></head>
<body>
    <!-- 페이지 본문 -->
    <div th:replace="~{layout/footer :: scripts}"></div>
</body>
</html>
```

**(2) 레이아웃 데코레이터 방식**
`nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect`를 쓰면 상속형 레이아웃이 가능하지만
**현재 의존성에 없으므로 이 문서의 범위 밖**입니다.

✅ **(1) fragment 분리 방식이 그대로 적용되었다.** 실제 fragment 이름은 `head`/`scripts` 대신
`header`/`footer`를 사용했다(`th:replace="~{layout/header :: header}"`,
`th:replace="~{layout/footer :: footer}"`). CSRF 메타 태그도 예시 그대로 `header.html`에 추가했다.

### 6.2.3 index.html 전환 예시 (실제 구현과 유사)

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head th:replace="~{layout/header :: head}"></head>
<body>

<h1>스프링부트로 시작하는 웹 서비스 Ver.2</h1>
<div class="col-md-12">
    <div class="row">
        <div class="col-md-6">
            <a href="/posts/save" role="button" class="btn btn-primary"
               sec:authorize="isAuthenticated()">글 등록</a>

            <span sec:authorize="isAuthenticated()">
                Logged in as: <span id="user" sec:authentication="name"></span>
                <a href="/logout" class="btn btn-info active" role="button">Logout</a>
            </span>

            <a th:href="@{/login}" class="btn btn-success active" role="button"
               sec:authorize="isAnonymous()">로그인</a>
        </div>
    </div>
    <br>

    <table class="table table-horizontal table-bordered">
        <thead class="thead-strong">
        <tr>
            <th>게시글번호</th><th>제목</th><th>작성자</th><th>최종수정일</th>
        </tr>
        </thead>
        <tbody id="tbody">
        <tr th:each="post : ${posts}">
            <td th:text="${post.id}"></td>
            <td><a th:href="@{'/posts/update/' + ${post.id}}" th:text="${post.title}"></a></td>
            <td th:text="${post.author}"></td>
            <td th:text="${#temporals.format(post.updatedAt, 'yyyy-MM-dd HH:mm')}"></td>
        </tr>
        </tbody>
    </table>
</div>

<div th:replace="~{layout/footer :: scripts}"></div>
</body>
</html>
```

**실제 구현과의 차이**: fragment 이름이 `head`/`scripts` 대신 `header`/`footer`이고,
`post.updatedAt` 대신 `post.modifiedDate`를 사용한다(`PostsListResponseDto` record 필드명,
[04장 4.4.3절](04-architecture-and-layers.md#443-postresponsedto--postslistresponsedto--구현-완료-record) 참고).
날짜 포맷팅(`#temporals.format(...)`)은 적용하지 않고 `LocalDateTime`을 그대로 출력했다(P2 후보).
실제 파일은 `src/main/resources/templates/index.html`을 참고.

**2026-09-09 추가**: `index.html`에 Bootstrap 페이지네이션 UI를 추가했다(P2-10, 페이징).
`IndexController`가 `postsPage`라는 모델 속성으로 `PostsPageResponseDto`를 전달하고, 템플릿은
`#numbers.sequence(0, postsPage.totalPages - 1)`로 페이지 번호를 나열한다.

```html
<nav th:if="${postsPage.totalPages > 1}">
    <ul class="pagination">
        <li class="page-item" th:classappend="${postsPage.first} ? 'disabled'">
            <a class="page-link" th:href="@{/(page=${postsPage.page - 1})}">이전</a>
        </li>
        <li class="page-item"
            th:each="i : ${#numbers.sequence(0, postsPage.totalPages - 1)}"
            th:classappend="${i == postsPage.page} ? 'active'">
            <a class="page-link" th:href="@{/(page=${i})}" th:text="${i + 1}"></a>
        </li>
        <li class="page-item" th:classappend="${postsPage.last} ? 'disabled'">
            <a class="page-link" th:href="@{/(page=${postsPage.page + 1})}">다음</a>
        </li>
    </ul>
</nav>
```

**주의(실제로 겪은 버그)**: Bootstrap의 `disabled` 클래스는 시각적 표시일 뿐 링크 클릭을 막지
않는다. 1페이지에서 "이전"을 클릭하면 `?page=-1`로 이동하는데, `IndexController`가 처음에는
`@RequestParam int page`로 받아 `PageRequest.of(page, size)`를 직접 생성했다가 `-1`에서
`IllegalArgumentException`(→ `500`)이 발생했다. `PostApiController`와 동일하게 `Pageable`을
`@PageableDefault`로 직접 받도록 바꿔, Spring Data의 `PageableHandlerMethodArgumentResolver`가
음수/범위초과 페이지를 자동으로 안전하게 보정하도록 해서 해결했다
([08장 8.6절](08-issues-and-todo.md#86-추가-구현-p2-10-페이징-2026-09-09) 참고).

`sec:authorize`, `sec:authentication`은 이미 선언된 `thymeleaf-extras-springsecurity6`가 제공하는 기능입니다.
이 방식을 쓰면 컨트롤러에서 `userName`을 `Model`에 담을 필요가 없습니다.

`#temporals`는 Thymeleaf가 `LocalDateTime`을 포맷하기 위한 유틸리티 객체로,
`thymeleaf-extras-java8time` 없이 Thymeleaf 3.1+에 내장되어 있습니다.

## 6.3 [해결됨] OAuth2 로그인 링크 문제

`index.html`은 다음 두 링크를 포함합니다.

```html
<a href="/oauth2/authorization/google" ...>Google Login</a>
<a href="/oauth2/authorization/naver"  ...>Naver Login</a>
```

이 경로는 **`spring-boot-starter-oauth2-client`가 제공하는 엔드포인트**입니다.
현재 의존성에는 `spring-boot-starter-security`만 있고 OAuth2 클라이언트가 없으므로
해당 링크는 **404를 반환**합니다.

또한 도메인 설계는 OAuth2가 아닌 **자체 인증**을 전제로 합니다.

- `User.password` 필드 존재 → 소셜 로그인만 쓴다면 불필요
- 클래스 주석의 "이메일 인증 시 일반 사용자로 승격" → 자체 회원가입 플로우

**따라서 기존 라이브러리 기준으로는 폼 로그인이 정합적입니다.** ✅ 실제로 `index.html`의 소셜
로그인 링크 2개를 `/login` 버튼 하나로 교체했다(`sec:authorize="isAnonymous()"` 조건부 표시).
`SecurityConfig`의 `formLogin()`이 기본 로그인 페이지를 제공하며, `POST /login`으로 인증 성공 시
`/`로 리다이렉트됨을 curl로 확인했다.

## 6.4 템플릿별 현황과 조치 — ✅ 전부 반영

| 파일 | 용도 | 분석 당시 문제 | 현재 상태 |
| --- | --- | --- | --- |
| `layout/header.html` | 공통 head | Thymeleaf fragment로 재구성 필요, CSRF 메타 태그 추가 필요 | ✅ `th:fragment="header"`, CSRF 메타 태그 2종 추가 |
| `layout/footer.html` | 공통 스크립트 | Thymeleaf fragment로 재구성 필요 | ✅ `th:fragment="footer"` |
| `index.html` | 게시글 목록 | Mustache 문법, OAuth2 링크, `modifiedDate` 필드명 불일치 | ✅ Thymeleaf 전환, `/login` 링크, `post.modifiedDate`로 필드명 통일 |
| `post/post-save.html` | 등록 폼 | Mustache include, `author` 입력 필드가 서버 인증 정책과 충돌 | ✅ Thymeleaf 전환, `author`는 `sec:authentication="name"` 읽기전용 표시(서버 전송 안 함) |
| `post/post-update.html` | 수정 폼 | Mustache 표현식, `label for="title"`이 3회 중복 | ✅ Thymeleaf 전환(`th:value`/`th:text`), label for 오류 수정 확인(curl로 렌더링 결과 대조). **2026-09-10**: `xmlns:sec` 추가 + 댓글 목록/작성 폼 추가(P3, 6.4.3절) |
| `user/signup.html` | 회원가입 폼 | (구현 전에는 파일 자체가 없었음) | ✅ **2026-09-10 신규(P3)** — `post-save.html`과 동일 구조 |
| `user/verify-result.html` | 이메일 인증 결과 화면 | (구현 전에는 파일 자체가 없었음) | ✅ **2026-09-10 신규(P3)** — 인증 성공/실패 메시지 + 로그인 링크(6.4.5절) |

### 6.4.1 post-save.html 조치 — ✅ 적용 완료

작성자는 서버가 세션에서 결정하므로 **`author` 입력 필드를 제거**해야 합니다
([05장 5.3.1절](05-api-spec.md) 참고). 남긴다면 읽기 전용으로 로그인 사용자명을 표시합니다.

```html
<div class="form-group">
    <label for="author">작성자</label>
    <input type="text" class="form-control" id="author"
           sec:authentication="name" readonly>
</div>
```

동시에 `index.js`의 `save()`에서 `author` 전송을 제거합니다. ✅ 실제 `index.js`에서 `author` 필드가
빠졌음을 확인했다([05장 5.6절](05-api-spec.md#56-클라이언트-스크립트-분석-staticjsappindexjs---수정-완료) 참고).

### 6.4.2 post-update.html 조치 — ✅ 적용 완료

```html
<label for="title">글 번호</label>
<input type="text" class="form-control" id="id" value="{{post.id}}" readonly>
```

`label`의 `for` 속성이 실제 입력 요소 `id`와 어긋나 있습니다(`for="title"`인데 대상은 `id="id"`).
접근성 관점에서 각 `label`의 `for`를 대응하는 `id`로 정정해야 합니다.

전환 후:

```html
<div class="form-group">
    <label for="id">글 번호</label>
    <input type="text" class="form-control" id="id" th:value="${post.id}" readonly>
</div>
<div class="form-group">
    <label for="title">제목</label>
    <input type="text" class="form-control" id="title" th:value="${post.title}">
</div>
<div class="form-group">
    <label for="author">작성자</label>
    <input type="text" class="form-control" id="author" th:value="${post.author}" readonly>
</div>
<div class="form-group">
    <label for="content">내용</label>
    <textarea class="form-control" id="content" th:text="${post.content}"></textarea>
</div>
```

`textarea`는 `th:value`가 아니라 `th:text`를 사용해야 값이 들어갑니다.

실제 `bootRun` 후 `GET /posts/update/{id}` 응답을 curl로 확인한 결과, `label for="id"`가
`input id="id"`를 정확히 가리키고(수정 전에는 `for="title"`이었음), 제목/작성자/내용이 모두 정상
바인딩됨을 확인했다([09장 4절](09-implementation-summary.md#4-실제-검증-curl-기반-e2e) 참고).

### 6.4.3 post-update.html 댓글 영역 + user/signup.html — ✅ 신규 구현 (2026-09-10, P3)

`post-update.html`에 `xmlns:sec="http://www.thymeleaf.org/extras/spring-security"`를 추가하고
(index.html/post-save.html에는 이미 있었으나 이 파일에는 없었음), 기존 폼 아래에 댓글 영역을
붙였다:

```html
<ul class="list-group" id="comment-list">
    <li class="list-group-item" th:each="c : ${comments}">
        <div class="d-flex justify-content-between">
            <strong th:text="${c.author}"></strong>
            <small th:text="${c.createdAt}"></small>
        </div>
        <p th:text="${c.content}"></p>
        <span sec:authorize="isAuthenticated()">
            <button type="button" class="btn btn-sm btn-danger btn-comment-delete"
                    th:attr="data-id=${c.id}">삭제</button>
        </span>
    </li>
</ul>

<div sec:authorize="isAuthenticated()" class="form-group">
    <input type="hidden" id="comment-post-id" th:value="${post.id}">
    <textarea class="form-control" id="comment-content" placeholder="댓글을 입력하세요"></textarea>
    <button type="button" class="btn btn-primary" id="btn-comment-save">댓글 등록</button>
</div>
<div sec:authorize="isAnonymous()">
    <a href="/login">로그인 후 댓글을 작성할 수 있습니다.</a>
</div>
```

댓글 목록은 `IndexController.postsUpdate()`가 `CommentService.findByPostId(id)`로 서버사이드에서
함께 조회해 모델에 담는다(게시글 조회와 동일 패턴, 별도 AJAX GET 불필요). 삭제 버튼은
소유권과 무관하게 로그인한 모든 사용자에게 노출하고 실제 권한은 서버가 강제한다(근거는
[04장 4.8절](04-architecture-and-layers.md#48-댓글comment-계층---구현-완료-2026-09-10) 참고).

**`templates/user/signup.html`** (신규)은 `post-save.html`과 동일한 구조(header/footer fragment,
Bootstrap 폼)로 이름/이메일/비밀번호 입력과 `id="btn-signup"` 버튼을 담는다. `index.html`의
비로그인 영역(`sec:authorize="isAnonymous()"`)에 `/signup` 링크를 추가했다.

**검증**(curl): `GET /signup` → `200`, `id="btn-signup"` 포함 확인. 댓글 작성 후
`GET /posts/update/{id}`에서 `<p>내용</p>`, `btn-comment-delete`, `id="comment-content"`가 모두
렌더링됨을 확인. 비로그인으로 같은 페이지 접근 시 댓글 작성 폼(`#comment-content`)과 삭제 버튼이
전혀 렌더링되지 않고 "로그인 후 댓글을 작성할 수 있습니다." 링크만 보임을 확인했다.

### 6.4.4 사진 업로드 + 비밀번호 변경 화면 — ✅ 신규 구현 (2026-09-10, P3)

**`post-save.html`**에 파일 입력을 추가했다:

```html
<div class="form-group">
    <label for="picture"> 사진 </label>
    <input type="file" class="form-control-file" id="picture" accept="image/*">
</div>
```

`index.js`의 `main.save()`는 파일이 선택되어 있으면 먼저 `POST /api/v1/posts/images`로
업로드해 URL을 받고, 그 URL을 포함해 게시글을 등록하는 2단계 흐름으로 바뀌었다(`uploadImage()`
→ `doSave()`). 업로드는 `FormData`로 보내야 하므로 이 요청만 `processData: false,
contentType: false`를 지정한다(전역 CSRF 헤더 주입은 콘텐츠 타입과 무관하게 그대로 적용된다).

**`post-update.html`**은 `post.picture`가 있으면 읽기전용 `<img>`로 보여준다(수정 화면에서
사진 교체는 이번 범위에서 제외 — 등록 시 첨부만 지원):

```html
<div class="form-group" th:if="${post.picture}">
    <label>사진</label><br>
    <img th:src="${post.picture}" style="max-width:100%;" alt="게시글 사진">
</div>
```

**`templates/user/change-password.html`**(신규)은 `post-save.html`과 동일한 구조로 현재
비밀번호/새 비밀번호 입력과 `id="btn-change-password"` 버튼을 담는다. `index.html`의 로그인
사용자 영역에 "비밀번호 변경" 링크를 추가했다.

**검증**(curl): 업로드한 이미지가 `/images/{uuid}.png`로 실제 서빙되고(`GET`으로 원본과 동일한
바이트 크기 확인), 게시글 등록 후 `GET /posts/update/{id}` 응답에 `<img src="/images/...">`가
정확히 포함됨을 확인했다. 비밀번호 변경 화면에서 성공 후 옛 비밀번호 로그인이 실패하고 새
비밀번호 로그인이 성공함을 확인했다. 상세는
[08장 8.14절](08-issues-and-todo.md#814-추가-구현-p3-5-게시글-사진-업로드--p3-9-비밀번호-변경-화면-2026-09-10) 참고.

### 6.4.5 verify-result.html — ✅ 신규 구현 (2026-09-10, P3)

이메일 인증 링크(`GET /users/verify?token=...`)를 클릭했을 때 보여주는 결과 화면이다.
`IndexController.verifyEmail()`이 `EmailVerificationService.verify(token)`을 호출해 성공하면
`success=true`를, `IllegalArgumentException`이 발생하면 `success=false`+`message`(실패 사유)를
모델에 담아 이 뷰를 렌더링한다.

```html
<p th:if="${success}">이메일 인증이 완료되었습니다. 이제 게시글과 댓글을 작성할 수 있습니다.</p>
<p th:unless="${success}" th:text="${message}"></p>
<a href="/login" role="button" class="btn btn-primary">로그인하러 가기</a>
```

**HTTP 상태 코드는 성공·실패 모두 `200`이다** — 이메일 클릭으로 진입하는 화면이라 깨진 링크처럼
보이지 않도록 상태 코드가 아니라 화면 텍스트로만 성공/실패를 구분하도록 의도적으로 설계했다
(05장 5.3.9절과 동일한 설명). 상세 검증 결과는
[08장 8.15절](08-issues-and-todo.md#815-추가-구현-p3-4-이메일-인증-플로우-2026-09-10) 참고.

## 6.5 정적 리소스

| 경로 | 설명 |
| --- | --- |
| `static/js/app/index.js` | 게시글 CRUD AJAX. 상세 분석은 [05장 5.6절](05-api-spec.md) 참고. **2026-09-10**: `comment`(등록/삭제), `signup`(가입) 객체 추가(P3) — 기존 `main` 객체는 변경 없음, 해당 요소가 없는 페이지에서는 jQuery가 조용히 무시하는 기존 원칙 그대로. **2026-09-10(디자인 개선)**: `showToast()`/`extractErrorMessage()` 헬퍼 추가(6.6절 참고) |
| `static/css/style.css` | ✅ **2026-09-10 신규(디자인 개선)** — 브랜드 색상·카드형 레이아웃·토스트 스타일을 담은 로컬 커스텀 CSS. CDN이 아니므로 SRI 불필요. 상세는 6.6절 |

CSS는 로컬 커스텀 파일(`static/css/style.css`) 하나가 추가됐고, 이미지 디렉터리는 없으며
Bootstrap 4.3.1과 jQuery 3.3.1은 그대로 CDN으로 로드합니다(버전 변경 없음 — 이유는 6.6절 참고).

- ✅ **CDN 무결성 검증(SRI) — 2026-09-09 추가 구현.** `header.html`의 Bootstrap CSS, `footer.html`의
  jQuery/Bootstrap JS 3개 태그 모두 `integrity`/`crossorigin="anonymous"`를 추가했다. 해시는
  기억이나 문서에서 베낀 값이 아니라, **템플릿이 실제로 참조하는 URL에서 파일을 직접 내려받아
  `openssl dgst -sha384`로 계산**했다(잘못된 해시는 SRI 불일치로 브라우저가 리소스 로딩 자체를
  차단해 CSS/jQuery가 통째로 사라지는 심각한 회귀를 유발하므로, 추측이 아닌 실측값을 사용).
  상세 값과 계산 과정은 [08장 8.9절](08-issues-and-todo.md#89-추가-구현-p2-13-cdn-무결성-속성-2026-09-09) 참고.
- 오프라인/폐쇄망 배포가 예정되어 있다면 `static/` 하위로 내려받아 번들해야 합니다.
- ✅ **해결(2026-09-10)** — `Post.picture` 업로드 파일은 `/images/**`로 서빙된다(6.4.4절 참고).

## 6.6 프론트엔드 디자인·UX 개선 — ✅ 신규 구현 (2026-09-10)

기능 구현이 전부 끝난 뒤, 화면 자체가 최초 분석 시점 그대로 방치돼 있던 문제(로그인 상태 표시가
`index.html`에만 있고 `style="display:inline"`로 땜빵, 다른 화면에는 네비게이션 자체가 없음,
피드백이 전부 네이티브 `alert()`/`confirm()`)를 개선했다. **범위는 디자인·UX 개선으로 한정** —
백엔드 코드, `/api/v1/**` 계약, `SecurityConfig`는 전혀 건드리지 않았다.

### Bootstrap 버전은 그대로 유지 (4.3.1)
Bootstrap 5로 올리지 않기로 결정했다. jQuery는 `index.js`의 CSRF 헤더 주입·AJAX 로직 때문에
BS5로 가도 어차피 계속 필요해(BS5의 "jQuery 제거"라는 이점을 못 살림), 버전을 올리면
`form-group`/`form-control-file` 등 BS4 전용 클래스 전수 교체와 SRI 해시 재계산(7장·08장 8.9절
참고)만 위험으로 남는다. CDN URL을 전혀 바꾸지 않았으므로 **기존 3개 SRI 해시는 그대로 유효**하다.

### 신규 파일
- **`static/css/style.css`**: CDN이 아닌 로컬 파일이라 SRI 불필요. `:root` 커스텀 프로퍼티(브랜드
  색상·배경·테두리·라운드 반경), 네비게이션 바 스킨, 게시글 목록 테이블에 카드형 그림자/호버
  효과(`<table>`+`th:each` 구조 자체는 그대로 — 모델 바인딩 변경 없이 순수 CSS만으로 개선), 폼
  포커스링, 버튼 간격 유틸리티(`.btn-group-gap`), 토스트 위치(우상단 고정) 등을 담았다.
- **`layout/navbar.html`**(신규 fragment): 기존 `index.html`에만 있던
  `sec:authorize="isAuthenticated()"`/`isAnonymous()` 로그인 상태 분기를
  `<nav class="navbar navbar-expand navbar-dark navbar-kraft">` 컴포넌트로 재구성했다. **조건부
  권한 판정 로직은 문자 그대로 보존**하고 마크업만 재포장했다. 이 fragment를 `index.html` 뿐
  아니라 `post-save.html`/`post-update.html`/`signup.html`/`change-password.html`/
  `verify-result.html` 등 화면 6개 전체의 `<body>` 시작 직후에 삽입해, 어느 화면에서나 로그인
  상태와 주요 이동 경로(글 등록/비밀번호 변경/로그아웃/로그인/회원가입)가 일관되게 보이도록 했다.

### `layout/header.html` / `layout/footer.html` 보강
- `header.html`: `<meta name="viewport">` 추가(기존에 없어 모바일 반응형이 전혀 안 됐음),
  인라인 SVG data-URI 파비콘(새 바이너리 파일·SRI 이슈 없음), `/css/style.css` 링크 추가.
- `footer.html`: 모든 화면에 한 번만 포함되는 이 fragment에 **토스트 컨테이너**(Bootstrap 4
  Toast, 기본 숨김, JS로 수동 `.toast('show')` 호출)와 **댓글 삭제 확인 모달**(`#confirmDeleteModal`)
  을 추가했다.

### `index.js` — alert()/confirm() 전면 교체 (AJAX 경로·CSRF 로직은 무변경)
- `showToast(message, type)` 헬퍼 추가. 기존 성공 시 `alert('...')` 9곳을
  `showToast('...', 'success')`로 교체.
- 실패 시 `alert(JSON.stringify(error))`(원시 `ProblemDetail` JSON을 그대로 노출하던 문제)를
  `extractErrorMessage(error)`로 교체 — `error.responseJSON.detail`(이미 `ApiExceptionHandler`가
  반환하던 필드)만 추려 사람이 읽을 수 있는 메시지로 보여준다. **백엔드 응답 계약 변경 없음.**
- `comment.remove(id)`의 동기 `confirm('댓글을 삭제하시겠습니까?')` 패턴을 제거하고, 삭제할
  `id`를 저장한 뒤 모달을 여는 `confirmRemove(id)`로 분리했다. 실제 DELETE AJAX 호출은 모달의
  "삭제" 버튼 클릭 핸들러(`#btn-confirm-comment-delete`)로 옮겨 비동기 콜백 구조로 전환했다.
- **`$(document).ajaxSend`의 CSRF 헤더 주입 블록은 한 글자도 수정하지 않았다** — 모든 AJAX 호출이
  여기 의존하므로 최우선 보존 대상이었다.
- `window.location.href`/`window.location.reload()` 하드 네비게이션 패턴(저장/삭제 후 페이지
  이동·새로고침)은 이번 범위에서 의도적으로 그대로 뒀다 — 요청 범위(디자인+UX)를 벗어나는 더 큰
  JS 리팩터링이 필요해 별도 작업으로 남겨둔다.

### 나머지 템플릿 — 카드형 레이아웃 통일
`post-save.html`/`post-update.html`/`signup.html`/`change-password.html`/`verify-result.html`/
`index.html` 전부 최상단을 `<div class="container py-4">`로 감싸고, 폼이 있는 화면은
`.card-kraft` 박스 안에 넣어 중앙 정렬·여백을 통일했다. `post-update.html`의 댓글 삭제 버튼은
`.btn-comment-delete` 클릭 시 `confirmRemove()`를 호출해 모달을 열도록 그대로 연결돼 있다(마크업
`data-id` 속성은 변경 없음).

### 검증
`./gradlew clean test` — 91개 전부 통과(백엔드 미변경이므로 회귀 없음 확인). `bootRun` 후 curl로
전 화면(`/`, `/posts/save`, `/posts/update/{id}`, `/signup`, `/users/me/password`,
`/users/verify`) `200` 확인, `/css/style.css` 정상 서빙 확인, 로그인 후 `index.html`의 네비게이션
바에 `sec:authentication="name"`이 실제 이메일을 정확히 표시함을 확인, 게시글 등록 API 호출이
CSRF 헤더와 함께 여전히 `200`으로 성공함을 확인해 `ajaxSend` 로직이 손상되지 않았음을 재확인했다.

### [버그 수정] 네비게이션 바 버튼이 클릭되지 않던 문제 (2026-09-10)

디자인 개선 직후 사용자가 "글 등록/Login/회원가입 버튼이 눌러지지 않는다"고 보고했다. 실제
브라우저에서 재현해보니 `document.elementFromPoint()`로 좌표를 찍어봐도 확인 전에는 원인이
바로 보이지 않을 정도로 은밀한 CSS 버그였다.

**원인**: Bootstrap 4의 `.toast` 클래스는 기본 상태에서 `opacity: 0`만 적용할 뿐
`display: none`은 적용하지 않는다(`display: none`은 별도의 `.hide` 클래스가 있어야 적용됨 —
Bootstrap의 Toast JS가 `show()` 호출 시 `.hide` 클래스를 제거하는 방식으로 동작하기 때문에,
초기 마크업에 `.hide`가 빠지면 항상 "투명하지만 여전히 클릭을 받는" 상태로 남는다). `footer.html`
에 토스트를 추가할 때 `class="toast"`만 쓰고 `hide`를 빠뜨렸는데, 이 투명한 토스트 박스가
`#toast-container`의 `position: fixed; top: 1rem; right: 1rem; z-index: 1080` 스타일 때문에
화면 우상단에 항상 떠 있었고, 하필 그 자리가 네비게이션 바의 "글 등록"/"Login"/"회원가입" 버튼
위치와 겹쳐서 클릭을 전부 가로챘다.

**수정**: `footer.html`의 `#app-toast`에 초기 클래스를 `class="toast hide"`로 변경(Bootstrap의
`show()`가 기대하는 초기 상태와 일치시킴). 추가로 `style.css`에 방어적 규칙을 넣어 같은 종류의
버그가 재발해도 클릭을 막지 않도록 했다: `#toast-container`와 내부 `.toast`에
`pointer-events: none`을 기본값으로 주고, 실제로 보이는 `.toast.show` 상태에서만
`pointer-events: auto`로 되돌린다.

**검증**: `document.elementFromPoint()`로 Login 링크 위치를 찍어 링크 자신이 최상단 엘리먼트임을
확인한 뒤, 실제 브라우저 클릭으로 글 등록/Login/회원가입 3개 버튼이 각각 `/posts/save`,
`/login`, `/signup`으로 정상 이동함을 재확인했다. `./gradlew test` 91개 재확인(회귀 없음).

### 로그인 화면 커스텀 템플릿 추가 (2026-09-10)

사용자가 `/login` 접속 시 "Please sign in / Username / Password" 같은 영문 무스타일 화면이
나온다고 보고했다. 원인은 `SecurityConfig`가 `formLogin()`에 `loginPage(...)`를 지정하지 않아
Spring Security가 **자체 내장 기본 로그인 페이지**(`DefaultLoginPageGeneratingFilter`가 생성하는
하드코딩된 HTML)를 그대로 보여주고 있었기 때문이다 — 이 페이지는 우리 Thymeleaf
`layout/header·footer·navbar` fragment나 `static/css/style.css`를 전혀 거치지 않는, 애초에
저희가 만든 템플릿이 아니었다(P1-9 단계부터 의도적으로 전용 화면을 만들지 않고 넘어간 부분,
[08장](08-issues-and-todo.md) 참고). 다른 6개 화면은 전부 디자인을 손봤지만 `/login`만 원래
템플릿이 없어 이번 디자인 개선 범위에서 빠져 있었다.

- **`templates/user/login.html`**(신규): 다른 화면과 동일한 `navbar` + `card-kraft` 레이아웃.
  이메일(`name="username"`)/비밀번호(`name="password"`) 필드는 Spring Security 기본 파라미터명
  그대로 사용, `th:name="${_csrf.parameterName}" th:value="${_csrf.token}"` 히든 필드로 CSRF
  토큰을 넣었다(폼이 AJAX가 아닌 일반 POST이므로 `index.js`의 헤더 기반 CSRF 주입과는 별개 경로).
  `${param.error}`/`${param.logout}` 모델 속성으로 실패/로그아웃 메시지를 카드 상단 alert로
  표시한다.
- **`SecurityConfig.formLogin()`에 `.loginPage("/login")` 추가**: `/login`(GET/POST)은
  기존 `anyRequest().permitAll()` catch-all이 이미 커버하고 있어 **`authorizeHttpRequests`
  규칙은 전혀 수정하지 않았다** — 이번에도 매처 표를 먼저 확인한 뒤 진행하는 원칙을 지켰다.
- **`IndexController`에 `GET /login` 매핑 추가**: `loginPage(...)`를 지정하면 Spring Security의
  자동 생성 필터가 비활성화되므로, 실제로 그 경로를 렌더링할 컨트롤러가 있어야 한다.

**검증**: 서버 재기동 후 curl로 `/login` 응답 본문이 우리 템플릿(네비게이션 바 포함)으로
바뀌었음을 확인, 브라우저로 잘못된 비밀번호 입력 → "이메일 또는 비밀번호가 올바르지 않습니다"
alert 표시 → 올바른 비밀번호로 재시도 → `/`로 리다이렉트되고 네비게이션 바에 이메일이 표시됨을
확인했다. `./gradlew test` 91개 재확인(기존 `redirectedUrl("/login")` 단언들은 로그인 페이지
URL 자체가 안 바뀌었으므로 수정 없이 통과).

### [회귀 발견·수정] `/logout`이 404로 깨짐 (2026-09-10)

사용자가 "로그인 페이지처럼 빠진 화면이 더 없는지" 확인해 달라고 요청해 전 화면을 재점검하던 중,
직접 `.loginPage("/login")`을 추가한 부작용으로 **`/logout`이 완전히 깨져 있는 것을 발견했다**
(Whitelabel Error Page, `404`). 원인: Spring Security는 `DefaultLoginPageGeneratingFilter`와
`DefaultLogoutPageGeneratingFilter`를 한 세트로 묶어서 관리한다 — `formLogin().loginPage(...)`로
로그인 화면을 커스텀하는 순간 로그인용 자동 생성 필터뿐 아니라 **로그아웃 확인 페이지 자동 생성
필터까지 함께 비활성화**된다. 기존에는 GET `/logout` 요청을 이 필터가 가로채 "정말
로그아웃하시겠습니까?" 확인 화면(내부적으로 POST 폼 포함)을 자동으로 그려줬는데, 그 필터가
사라지면서 GET `/logout`을 받아줄 곳이 없어져 404가 난 것이다.

사용자가 명시적으로 요구한 동작은 두 가지였다: **(1) 확인 페이지 없이 바로 로그아웃**, **(2)
로그아웃 직후에는 항상 메인이 아니라 로그아웃을 누른 그 페이지로 되돌아갈 것.** 최신 Spring
Security는 CSRF 보호가 켜져 있으면 로그아웃 처리 자체를 **POST**로만 받는다(GET 기반 로그아웃은
CSRF에 취약해 제거됨) — 예전에 GET으로도 "동작하는 것처럼" 보인 건 자동 생성 확인 페이지가
그 다리 역할(GET으로 열어 보여주고, 그 안의 폼이 실제로는 POST)을 해줬기 때문이었다.

- **`layout/footer.html`**: 모든 화면에 포함되는 숨겨진 `<form id="logout-form" th:action="@{/logout}" method="post">`를 CSRF 히든 필드와 함께 추가했다.
- **`layout/navbar.html`**: `<a href="/logout">Logout</a>`를 `<button type="button" id="btn-logout">Logout</button>`로 변경 — 더 이상 GET 링크가 아니다.
- **`index.js`**: `$('#btn-logout')` 클릭 시 `$('#logout-form').trigger('submit')`로 즉시 POST 로그아웃(확인 페이지 없음). 비밀번호 변경 성공 후 자동 로그아웃하던 `window.location.href = '/logout'`(GET, 이제 깨짐)도 동일한 폼 제출 방식으로 교체했다.
- **`SecurityConfig`에 `refererLogoutSuccessHandler()` 추가**: `logoutSuccessUrl("/")` 고정 대신, 로그아웃 요청의 `Referer` 헤더를 읽어 **같은 오리진일 때만** 그 페이지로 리다이렉트하고, `Referer`가 없거나 외부 도메인이면 `/`로 안전하게 대체한다(오픈 리다이렉트 방지). 폼이 매 페이지의 네비게이션 바 안에서 제출되므로 `Referer`는 항상 로그아웃을 누른 바로 그 페이지가 된다.

**검증**: 로그인 후 `/posts/save`(글 등록 화면)로 이동한 상태에서 네비게이션 바의 Logout 버튼
클릭 → 확인 페이지 없이 즉시 로그아웃 → **`/posts/save`(메인이 아님)로 그대로 복귀**하고
네비게이션 바가 비로그인 상태(Login/회원가입)로 전환됨을 브라우저로 확인했다. `./gradlew test`
91개 재확인(기존 로그아웃 관련 단언 없음, 회귀 없음).

### 전 화면 점검 결과 (2026-09-10)

`/login`·`/logout` 두 건을 고친 뒤, "로그인 페이지처럼 빠진 곳이 더 있는지" 전 화면을 점검했다.

- **점검 대상**: `IndexController`의 모든 `@GetMapping`(`/`, `/posts/save`, `/posts/update/{id}`,
  `/signup`, `/login`, `/users/me/password`, `/users/verify`) + `templates/` 아래 모든 `.html`
  파일(고아 템플릿 여부 확인) — 전부 컨트롤러에 연결되어 있고 이번 디자인 개선 6개 화면 + 이번에
  고친 로그인 화면까지 전부 커버됨을 확인했다. 고아 템플릿 없음.
- **의도적으로 범위 밖에 남긴 것(신규 발견 아님, 기존에 이미 문서화된 결정)**: 화면 컨트롤러
  (`IndexController`)에서 발생하는 예외는 `web.api` 패키지 전용인 `ApiExceptionHandler`가 적용되지
  않아 Spring Boot 기본 Whitelabel Error Page(404/500)가 그대로 노출된다. 이는 이번 재점검에서
  새로 발견한 문제가 아니라 P1 단계부터 의도적으로 남겨둔 스코프 경계다(05장 5.4절 표의
  "화면 컨트롤러의 오류" 행 참고). `/h2-console`은 H2가 제공하는 서드파티 도구 UI라 애초에
  스킨 대상이 아니다.
