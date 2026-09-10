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
빠졌음을 확인했다([05장 5.6절](05-api-spec.md#56-클라이언트-스크립트-분석-staticjsappindexjs--수정-완료) 참고).

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
[04장 4.8절](04-architecture-and-layers.md#48-댓글comment-계층--구현-완료-2026-09-10) 참고).

**`templates/user/signup.html`** (신규)은 `post-save.html`과 동일한 구조(header/footer fragment,
Bootstrap 폼)로 이름/이메일/비밀번호 입력과 `id="btn-signup"` 버튼을 담는다. `index.html`의
비로그인 영역(`sec:authorize="isAnonymous()"`)에 `/signup` 링크를 추가했다.

**검증**(curl): `GET /signup` → `200`, `id="btn-signup"` 포함 확인. 댓글 작성 후
`GET /posts/update/{id}`에서 `<p>내용</p>`, `btn-comment-delete`, `id="comment-content"`가 모두
렌더링됨을 확인. 비로그인으로 같은 페이지 접근 시 댓글 작성 폼(`#comment-content`)과 삭제 버튼이
전혀 렌더링되지 않고 "로그인 후 댓글을 작성할 수 있습니다." 링크만 보임을 확인했다.

## 6.5 정적 리소스

| 경로 | 설명 |
| --- | --- |
| `static/js/app/index.js` | 게시글 CRUD AJAX. 상세 분석은 [05장 5.6절](05-api-spec.md) 참고. **2026-09-10**: `comment`(등록/삭제), `signup`(가입) 객체 추가(P3) — 기존 `main` 객체는 변경 없음, 해당 요소가 없는 페이지에서는 jQuery가 조용히 무시하는 기존 원칙 그대로 |

CSS/이미지 디렉터리는 없으며 Bootstrap 4.3.1과 jQuery 3.3.1을 CDN으로 로드합니다.

- ✅ **CDN 무결성 검증(SRI) — 2026-09-09 추가 구현.** `header.html`의 Bootstrap CSS, `footer.html`의
  jQuery/Bootstrap JS 3개 태그 모두 `integrity`/`crossorigin="anonymous"`를 추가했다. 해시는
  기억이나 문서에서 베낀 값이 아니라, **템플릿이 실제로 참조하는 URL에서 파일을 직접 내려받아
  `openssl dgst -sha384`로 계산**했다(잘못된 해시는 SRI 불일치로 브라우저가 리소스 로딩 자체를
  차단해 CSS/jQuery가 통째로 사라지는 심각한 회귀를 유발하므로, 추측이 아닌 실측값을 사용).
  상세 값과 계산 과정은 [08장 8.9절](08-issues-and-todo.md#89-추가-구현-p2-13-cdn-무결성-속성-2026-09-09) 참고.
- 오프라인/폐쇄망 배포가 예정되어 있다면 `static/` 하위로 내려받아 번들해야 합니다.
- `Post.picture` 기능을 구현하면 업로드 파일을 서빙할 경로 설계가 추가로 필요합니다.
