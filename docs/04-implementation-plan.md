# 04. 구현 계획과 검증 기준

상태: 1~5단계 구현·검증 완료(2026-09-10, `feature/responsive-frontend-phase1-2` 브랜치). 아래 5단계 작업 순서 표와 최종 수용 기준은 실제 결과로 갱신했다. 이 문서는 애초에 실제 구현을 위한 작업 순서로 작성된 것이며, 완료 표시는 코드·테스트·수동 검증으로 뒷받침된다.

## 1. 적용 전략

현재 Thymeleaf 화면과 REST API를 유지하면서 공통 디자인, 목록, 글 읽기·쓰기, 계정 화면 순서로 적용한다. 첫 작업에서는 서버 계약에 없는 정보를 요구하지 않는 공통 영역과 목록을 완성한다.

현재 스펙의 조회·등록·수정·삭제와 폼 로그인·CSRF 동작을 보존한다. 읽기·편집 구분, 관리 버튼 표시, 정상적인 오류 화면처럼 목표 UX에 필요한 서버 보완은 별도 작업으로 추적한다.

## 2. 변경 대상 파일

| 파일 | 구현할 내용 | 의존성 |
| --- | --- | --- |
| [style.css](../src/main/resources/static/css/style.css) | 디자인 토큰, 공통 Grid, 브레이크포인트, 목록·폼·상태 스타일 | 디자인 방향·반응형 명세 |
| [layout/header.html](../src/main/resources/templates/layout/header.html) | 제목·메타·기본 자산 정리, CSRF 메타 유지 | 화면별 제목 전달 방식 |
| [layout/navbar.html](../src/main/resources/templates/layout/navbar.html) | 브랜드·메뉴 그룹·계정 상태·작은 화면 메뉴 | 현재 인증 정보 |
| [layout/footer.html](../src/main/resources/templates/layout/footer.html) | 결과 메시지, 공통 삭제 모달, 로그아웃 폼·스크립트 유지 | JS 모달·결과 전달 방식 |
| [index.html](../src/main/resources/templates/index.html) | 전체 게시글 제목, 적응형 목록, 빈 상태, 제한된 페이지 창, 안내 영역 | 기존 `posts`, `postsPage` |
| [post-save.html](../src/main/resources/templates/post/post-save.html) | 넓은 작성 폼, 이미지 안내·미리보기, 익명 로그인 안내 | 업로드·저장 상태 관리 |
| [post-update.html](../src/main/resources/templates/post/post-update.html) | 읽기·편집 상태 분리, 권한별 관리 버튼, 댓글 수정 | 서버의 관리 가능 여부 |
| [user 템플릿](../src/main/resources/templates/user) | 중앙 폼, 입력 도움말, 지속적인 결과·오류, 인증 결과 문구 | 기존 인증·검증 규칙 |
| [index.js](../src/main/resources/static/js/app/index.js) | 메뉴, 중복 제출 방지, 업로드 단계, 댓글 편집, 삭제 확인, 상태·포커스 관리 | DOM과 API 계약 |
| [IndexController.java](../src/main/java/com/kraft/web/IndexController.java) | 관리 권한이 포함된 화면 데이터 연결 | 서비스 조회·인증 정보 |
| [PostService.java](../src/main/java/com/kraft/service/post/PostService.java), [CommentService.java](../src/main/java/com/kraft/service/comment/CommentService.java) | 화면용 조회에서 본인·관리자 여부 계산 | `OwnershipPolicy`와 동일한 정책 |
| 화면용 오류 처리·템플릿 (신규 제안) | 없는 게시글 등의 HTML 오류와 목록 복귀 | 화면 컨트롤러에 한정된 예외 처리 |
| [application.yml](../src/main/resources/application.yml) 및 실행 환경 | 서비스의 5MB 검사와 multipart·요청 수신 제한 정합성 확인 | 실제 업로드 경계 검증 |

읽기·편집 화면 분리는 기존 템플릿 안에서 시작한다. 반복되는 목록·댓글·탐색 마크업만 필요할 때 Thymeleaf fragment로 분리한다. 프레임워크·라이브러리 교체를 레이아웃 작업과 함께 진행하지 않는다.

## 3. 필수 선행 보완과 별도 기능 작업

### 목표 화면에 필요한 보완

| ID | 작업 | 완료 기준 |
| --- | --- | --- |
| V1 | 게시글 `canManagePost`, 댓글별 `canManage` 화면 모델 | 익명·타인 false, 작성자·관리자 true. 공개 REST DTO에 작성자 이메일을 추가하지 않음 |
| V2 | 읽기 기본 상태와 권한 있는 편집 진입 | 비소유자에게 수정 입력·관리 버튼이 노출되지 않음. API에서도 기존 권한 검사 유지 |
| V3 | 현재 API를 이용한 댓글 수정 UI | 내용 유지·취소·성공·오류·권한 상태 정상 |
| V4 | 없는 게시글의 화면용 오류 처리 | HTML 요청에 사용자 안내와 목록 복귀 제공. REST 오류 형식과 혼합하지 않음 |
| V5 | 이동 후 결과 메시지 전달 | 등록·수정·댓글·가입·비밀번호 변경 결과가 목적 화면에서 한 번 표시됨 |
| V6 | 실제 인증 정책과 일치하는 문구 | 미구현 재발송이나 인증 전 작성 차단을 안내하지 않음 |
| V7 | 업로드 안내와 실제 수신 제한 일치 | 5MB 파일·multipart 요청을 받을 수 있는지 확인. 크기 초과는 사용자에게 용량 오류로 표시 |

V1은 인증 객체와 엔티티를 이용할 수 있는 서비스 조회 경로에서 계산하고 화면용 모델로 전달하는 방식을 우선한다. `author` 이름과 로그인 이메일을 비교하지 않는다. 댓글마다 사용자 조회를 추가하는 방식도 피한다. 공개 GET API의 기존 필드는 유지할 수 있다.

V4는 없는 게시글이라는 원인을 구별할 수 있는 화면 조회 예외와 처리기를 사용한다. 모든 `IllegalArgumentException`을 무조건 404로 치환하지 않는다. 기존 화면 예외 전파를 검증하던 테스트는 변경된 화면 계약에 맞게 갱신한다.

### 현재 기능 범위와 분리할 항목

| 항목 | 필요한 이유 / 처리 방침 |
| --- | --- |
| 이메일 인증 전 작성 제한 | 현재 `authenticated()` 정책 변경이 필요. `USER/ADMIN` 검사와 화면 상태를 함께 정의하는 별도 기능 작업 — **완료(2026-09-11)**: `WriteAccessPolicy.requireVerified(User)`를 신설해 `Role.GUEST`면 `AccessDeniedException`(403)을 던지도록 `PostService.save`·`CommentService.save`에 적용했다. 네비게이션에 GUEST 전용 "인증 메일 재발송" 버튼을 추가했다 |
| 인증 메일 재발송·전송 상태 | API·실패 복구 계약이 필요. 현재 화면에 동작하지 않는 버튼 추가 금지 — **완료(2026-09-11)**: `EmailVerificationService.resend(email)`이 기존 토큰을 지우고 재발송한다(실패를 흡수하지 않고 그대로 전파). `POST /api/v1/users/me/verify-email/resend` API와 위 재발송 버튼으로 노출한다 |
| 로그인 후 원래 위치 복귀 | 현재 강제 `/` 이동 정책과 충돌. 별도 인증 흐름 변경 — **완료(2026-09-11)**: 이 앱은 모든 화면 경로가 `permitAll`이라 Spring Security 기본 `RequestCache`가 채워질 일이 없다는 것을 확인했다. 대신 네비게이션의 로그인 링크가 현재 경로를 `?redirect=`로 실어 보내고, 로그인 폼 히든 필드로 POST까지 옮겨 커스텀 `AuthenticationSuccessHandler`가 그 경로로 돌려보낸다(외부 URL·프로토콜 상대 URL은 "/"로 안전하게 대체) |
| 비밀번호 변경 후 로그인으로 고정 이동 | 현재 로그아웃 복귀 정책 조정 필요 — **완료(2026-09-11)**: `refererLogoutSuccessHandler`에 `/users/me/password` 예외를 추가해 Referer와 무관하게 항상 `/login`으로 보낸다(`/posts/save` 예외와 같은 패턴) |
| 가입·변경 비밀번호 규칙 통일 | 서버 검증 정책 변경 필요. 현재 UI는 각 API 규칙을 정확히 안내 — **완료(2026-09-11)**: `ChangePasswordRequestDto.newPassword`에 `SignUpRequestDto.password`와 동일한 대소문자·특수문자 `@Pattern`을 추가했다 |
| 제목·이름 최대 길이의 API 검증 | DB 길이에 맞춘 요청 DTO 검증 보완. 클라이언트 제한만으로 API 검증을 대체하지 않음 — **완료(2026-09-11)**: `SignUpRequestDto.name`(`@Size(max=50)`), `PostSaveRequestDto`·`PostUpdateRequestDto.title`(`@Size(max=255)`)에 DB 컬럼 길이와 일치하는 검증을 추가하고 `PostApiControllerTest`·`UserApiControllerTest`에 초과 시 400을 확인하는 테스트를 추가했다(`gradlew.bat test` 전체 통과) |
| 게시글 삭제 시 댓글 처리 | 서비스·엔티티·DB 관계를 확인해야 함. 연관 댓글이 있는 삭제의 실패가 재현되면 기능 회귀 작업으로 처리 — **완료(2026-09-11)**: 실제로 `comments.post_id` FK 제약(cascade 없음)으로 인해 댓글이 있는 게시글을 삭제하면 `TransientPropertyValueException`으로 실패함을 통합 테스트로 재현했다. `CommentRepository.deleteAllByPostId(postId)`를 추가하고 `PostService.delete()`에서 게시글 삭제 전에 호출하도록 수정했다. `CommentRepositoryTest`에 재현·회귀 방지 테스트, `PostServiceTest`에 호출 순서(`commentRepository.deleteAllByPostId` → `postRepository.delete`) 검증 테스트를 추가했다(`gradlew.bat test` 전체 통과) |
| 업로드 파일 정리·기존 이미지 변경 | 파일 삭제·수정 API와 정리 정책 필요 — **완료(2026-09-11)**: `PostImageService.deleteIfExists(url)`을 추가해(경로 조작 방어 포함) `PostService.delete()`가 삭제된 게시글의 이미지 파일을 지우고, `PostService.update()`는 picture가 바뀌면 이전 파일을 지운다. `PostUpdateRequestDto.picture`를 추가하고 `post-update.html`에 이미지 교체·삭제 UI를 이식했다. 로컬 `bootRun` + `claude-in-chrome`으로 교체·삭제 후 실제 파일이 지워지는 것까지 확인했다 |
| 검색·분류·조회 수·추천·인기글·댓글 수 | 데이터 모델·조회 API 확장 필요 — **완료(2026-09-11)**: `Post.category`(`Category` enum: 자유/질문/공지, 기본 FREE)·`viewCount`(상세 조회마다 +1, 중복 방지 없음) 필드와 `PostLike` 엔티티(사용자당 게시글별 1회 토글)를 추가했다. `PostRepository.search(keyword, category, pageable)`로 제목·본문 LIKE 검색과 분류 필터를 함께 지원하고, `findTopByViewCountDesc`로 조회수 상위 인기글을 뽑는다. 목록 화면에 검색창·분류 필터·인기글 섹션·조회수·댓글 수 컬럼을 추가했고, 상세 화면에 추천 버튼을 추가했다(`PUT /api/v1/posts/{id}/like`). 운영 스키마는 Flyway로 전환했다(아래 §8 참고) |

## 4. 단계별 작업 순서

| 단계 | 작업 | 단계 완료 조건 | 상태 |
| --- | --- | --- | --- |
| 1. 공통 영역·목록 | 토큰, 공통 헤더·탐색, 목록, 페이지 이동, 빈 상태 | 320·768·992·1200·1440px에서 목록과 메뉴 이용 가능 | 완료 |
| 2. 권한·읽기·댓글 | V1·V2·V3·V4, 상세 읽기, 편집, 삭제 확인 | 익명·타인·본인·관리자의 화면과 API 권한 일치 | 완료 |
| 3. 글쓰기 | V7, 작성 폼, 로컬 미리보기, 업로드와 저장 단계, 재시도 | 이미지 유무·실패·중복 클릭에서 올바른 요청과 입력 보존 | 완료 |
| 4. 계정·피드백 | 계정 폼, 인증 결과, V5·V6, 접근성 상태 처리 | 현재 인증·로그아웃·검증 계약과 안내 일치 | 완료 |
| 5. 통합 검증 | 화면·기능·접근성·실패 사례 점검 | 아래 수용 기준 충족 및 실제 캡처 검토 | 부분 완료(아래 §7 참고) |

1단계를 먼저 검토할 수 있는 단위로 만들고, 그 결과를 상세·계정 화면에 공통 적용했다. Chrome 개발자 도구로 데스크톱·모바일 화면을 확인하며 밀도·간격을 조정했다. 실제 기기·다른 브라우저(Edge·Safari)·화면 캡처 회의체 검토는 이번 범위에 포함하지 않았다.

3단계 구현 중 실측으로 두 가지 실제 결함을 발견해 함께 고쳤다:
1. `application*.yml`에 multipart 설정이 없어 Spring Boot 기본값(`max-file-size=1MB`)이 적용되던 문제 — `PostImageService`의 5MB 검사·화면 안내와 불일치해 1~5MB 파일이 원인 불명 오류로 거부되었다. `spring.servlet.multipart.max-file-size: 6MB`로 서비스 검사보다 한 단계 위에 컨테이너 한도를 두어 5MB 판정자를 서비스 하나로 일원화했다.
2. `resolve-lazily: true` 없이는 컨트롤러 진입 전에 멀티파트가 즉시 파싱되어, 한도 초과 시 커넥션이 리셋되고 `ApiExceptionHandler`의 413 JSON 응답이 클라이언트에 도달하지 못했다(로컬 curl 실측으로 확인). `resolve-lazily: true`를 추가해 예외가 정상적인 컨트롤러 예외 처리 흐름 안에서 발생하도록 했다.

1단계 검증 중에는 헤더 메뉴 토글의 `display:none` CSS가 소스 순서상 기본 선언보다 앞에 있어 992px 이상에서도 계속 보이던 버그를 발견해 수정했다.

## 5. 구현 시 보존할 연결

- 로그인 폼의 `username`, `password`, CSRF 필드와 POST 목적지.
- CSRF 메타와 jQuery AJAX 헤더 주입, 이미지 업로드에도 동일 적용.
- 게시글의 숨김 ID, 댓글의 게시글 ID·댓글 ID, 이미지 multipart 필드명 `file`.
- 기존 JS 선택자를 유지하거나 템플릿·이벤트를 같은 변경에서 함께 갱신.
- 화면마다 반복되는 댓글에는 중복 HTML ID를 만들지 않음.
- 제목·본문·작성자·댓글은 텍스트로 출력하고 사용자 입력을 `innerHTML`로 삽입하지 않음.
- Bootstrap 4.3.1 환경에서 Bootstrap 5 전용 `data-bs-*`·offcanvas 사용 금지. 기존 버전에서 가능한 메뉴·모달 동작 사용.
- 새 전역 CSS가 계정 폼·모달·토스트의 Bootstrap 스타일에 주는 영향 확인.

결과 메시지는 서버 flash 또는 허용된 메시지 키를 담는 일회성 `sessionStorage` 등 한 가지 방식을 선택한다. 메시지를 표시하면 즉시 제거하고, 비밀번호·본문·토큰 같은 사용자 입력은 결과 전달 저장소에 넣지 않는다. 저장소를 쓰는 경우 사용 불가 상황에도 기본 이동은 작동해야 한다.

## 6. 검증 계획

### 코드·기능 검증

현재 테스트는 [IndexControllerTest](../src/test/java/com/kraft/web/IndexControllerTest.java), [API 테스트](../src/test/java/com/kraft/web/api), [서비스 테스트](../src/test/java/com/kraft/service)에 존재한다. 구현 시 변경된 계약에 해당하는 테스트를 갱신하고 저장소 루트에서 아래 명령을 실행한다.

```powershell
.\gradlew.bat test
```

Java 25 환경을 사용한다. `gradlew.bat test`는 매 단계 구현 직후 실행해 전체 통과를 확인했다(1·2단계 32개, 3·4단계 반영 후에도 회귀 없음). 아래 표는 수동 검증 결과이며, 확인 방법과 실제로 실행한 범위를 함께 남긴다.

| 구분 | 확인 시나리오 | 기대 결과 | 확인 방법 / 범위 |
| --- | --- | --- | --- |
| 목록 | 0개·1개·다중 페이지·범위 초과(`?page=999`) | 빈 상태 구분, 잘못된 페이지 링크 없음 | Chrome 확인 완료. 10개 초과로 여러 페이지가 되는 경우는 1단계에서 12개로 확인, 이번 단계는 0/1/999 위주로 재확인 |
| 경계 폭 | 991/992px | 메뉴·열 배치가 끊기지 않음 | Chrome으로 991·1008px 확인. 575/576·767/768·1199/1200px 개별 경계 스크린샷은 이번 라운드에 재실행하지 않음(1단계에서 320~1440px 대표 폭으로 확인) |
| 권한 | 익명·본인, API 403/400 | 관리 버튼 노출과 API 판정 일치 | curl로 API 403/400 확인(1단계), 화면 노출은 로그인 세션 전환으로 확인. GUEST/ADMIN 역할별 화면은 별도 계정으로 재확인하지 않음 |
| 편집 | 저장·취소 | 원본 복구·결과 안내 | Chrome으로 확인(제목 변경 후 취소 시 원복, 저장 후 flash 표시) |
| 업로드 | 파일 없음·300KB·1.1MB·5MB 경계·5.5MB·6.5MB·비허용 확장자 | 형식·용량 안내, 서버 검증 결과 반영 | curl로 전 구간 실측(§4의 1MB 기본값 버그 발견 지점). 클라이언트 사전 검사(확장자·5MB)는 Chrome에서 직접 확인 |
| 단계별 실패 | 업로드 성공 후 재사용 로직 | 동일 파일의 업로드 URL 재사용 | 코드 리뷰로 로직 확인(`uploadedForFile` 비교). 실제로 저장 단계만 실패시키는 네트워크 장애 주입은 하지 않음 — 후속 검증 항목으로 남김 |
| 댓글 | 등록·인라인 수정·취소 | 해당 댓글 대상으로 동작 | Chrome으로 확인 |
| 가입 | 비밀번호 확인 불일치, 성공 | 필드 오류·포커스 이동, 성공 후 로그인 화면 안내 | Chrome으로 확인(`aria-invalid`, 포커스, flash 메시지 모두 JS로 검사) |
| 인증 | 유효하지 않은 토큰, 유효한 토큰(로그인 상태) | 결과 표시, 미구현 행동으로 유도하지 않음 | Chrome으로 확인. 만료된 토큰·이미 사용한 토큰 경로는 코드로만 확인(24시간 대기가 필요해 실시간 재현 안 함) |
| 비밀번호 변경 | 현재 값 오류, 성공 | 현재 규칙 적용, 성공 후 로그아웃과 로그인 안내 | Chrome으로 확인 |
| 보안 필터 | CSRF 만료·403·JSON 대신 로그인 HTML | 성공 처리 금지, 안내 제공 | `extractErrorMessage()`의 401/403/파싱 실패 분기는 코드로 구현·리뷰했으나, 실제 세션·CSRF 만료를 인위로 재현해 브라우저로 확인하지는 않았다 — 후속 검증 항목으로 남김 |
| 게시글 삭제(댓글 있음) | 댓글이 달린 글 삭제 | 서버의 실제 성공·실패 반영 | 이번 라운드에 재현하지 않음(§3의 "현재 기능 범위와 분리할 항목" 참고) |

### 반응형·접근성 검증

실제로 수행한 범위와 남은 항목을 구분해 기록한다.

**확인함**
- 뷰포트 320, 375, 768, 820, 991/1008, 1280, 1440px에서 목록·상세·글쓰기·계정 화면(Chrome 개발자 도구 창 크기 조절).
- 992px 미만에서 헤더 메뉴 버튼으로 열고 닫기, Escape로 닫힘, 포커스가 토글 버튼으로 복귀.
- 마우스 클릭 기반 상호작용 전반(메뉴, 편집 진입·취소, 댓글 인라인 수정, 삭제 모달, 회원가입 필드 오류 포커스 이동)에서 콘솔 오류 0건(`read_console_messages`로 확인).
- 회원가입 비밀번호 불일치 시 `aria-invalid="true"`와 해당 입력으로 포커스 이동을 JS로 직접 검사.

**확인하지 않음 — 후속 작업으로 남김**
- 1280px 400% 확대, 200% 글자 확대, 가로 방향 모바일, 가상 키보드 표시 상태.
- 스크린리더(NVDA·VoiceOver 등)로 제목·랜드마크·라벨·오류·진행 상태 낭독 확인. `aria-live`·`aria-busy`·`role="alert"`/`"status"` 속성은 코드에 반영했으나 실제 스크린리더로 들어보지 않았다.
- 실측 색상 대비 검증(2단계 설계 문서의 계산값은 이론상 수치이며, 최종 렌더링된 CSS에 대한 자동/수동 재측정은 하지 않음).
- 데스크톱 Edge, 모바일 Safari에서 핵심 흐름 확인 — 이번 검증은 Chrome(개발자 도구 에뮬레이션)에서만 수행했다. 실제 기기 테스트도 하지 않았다.
- Tab 키만으로 전체 흐름(메뉴 진입부터 폼 제출까지)을 끝까지 따라가는 키보드 전용 주행은 부분적으로만 확인(포커스 이동 지점 개별 검사 위주).

### 후속 검증 (2026-09-11)

이번 라운드에서 위 "확인하지 않음" 항목 중 일부를 자동화로 추가 확인했다. 도구 제약으로 뷰포트·확대 검증은 시도했으나 수행하지 못했다.

**추가로 확인함**
- **실측 색상 대비**: `style.css`의 실제 `--kraft-*` 토큰 값으로 WCAG 대비비를 계산(본문 텍스트/배경, 흐림 텍스트/배경, 프라이머리·위험·accent·성공 색상, 헤더 배경 위 흰 글자 등 13개 조합). 전 조합 AA 기준(본문 4.5:1, 큰 텍스트·UI 3:1) 통과. 계산 스크립트는 이번 세션의 스크래치 파일로만 존재하며 저장소에는 포함하지 않았다.
- **키보드 전용 흐름**: 로그인(Tab+Enter), 게시글 상세 진입, 수정 진입(포커스가 제목 입력으로 이동), 수정 취소(포커스가 수정 버튼으로 복귀), 댓글 인라인 수정(포커스가 댓글 textarea로 이동), 삭제 확인 모달 열기(포커스가 모달로 이동)·Escape로 닫기(포커스가 삭제 버튼으로 복귀), 회원가입 비밀번호 불일치 후 Tab만으로 제출(포커스가 `passwordConfirm`으로 이동하고 `aria-invalid="true"` 설정)까지 마우스 없이 재현해 코드 설명과 실제 동작이 일치함을 확인. 단, 992px 미만에서만 노출되는 헤더 메뉴 토글의 키보드 흐름은 뷰포트 제약으로 이번에도 확인하지 못했다.
- **`aria-busy` 동적 반영**: 게시글 수정 저장 버튼(`#btn-update`)을 클릭한 직후 `aria-busy`가 `null`에서 `"true"`로 실제 DOM에 반영됨을 확인.
- **권한 화면 재확인**: 신규로 만든 USER 계정과, DB에서 역할을 직접 승격한 ADMIN 계정으로 로그인해 타인 소유 게시글에서 관리 버튼(수정·삭제)이 정상 노출됨을 재확인(`OwnershipPolicy.canManage()` 경로).

**시도했으나 도구 제약으로 수행하지 못함**
- **뷰포트 매트릭스(320~1920px)와 400%/200% 확대**: 이번 세션의 `claude-in-chrome` 브라우저 자동화 도구가 구동하는 탭은 `resize_window` 호출이 성공을 반환해도 실제 뷰포트가 항상 1920px로 고정되어 바뀌지 않았다(새 탭에서도 동일 재현, 사용자가 직접 Chrome 창을 조절한 뒤에도 변화 없음 — 자동화 세션이 사용자 화면과 분리된 고정 해상도로 렌더링되는 것으로 보인다). 페이지 확대 단축키(`ctrl+=`/`ctrl+-`)도 도구에서 명시적으로 차단되어 있다. 따라서 이번 라운드는 1920px 고정 뷰포트에서 가능한 항목만 수행했다.
  - **사용자가 직접 확인할 절차**: Chrome에서 대상 화면(`/`, `/posts/update/{id}`, `/posts/save`, `/signup`, `/login`, `/users/me/password`)을 열고, F12 → 기기 툴바(Ctrl+Shift+M) 또는 Ctrl+`+`/`-`로 320·375·576·768·820·991·992·1200·1280·1440·1920px와 400%/200% 확대를 직접 확인한다.

### 재시도 결과 (2026-09-11, 2차)

같은 항목을 다른 방법으로 다시 시도했으나 동일한 도구 제약을 재확인했을 뿐, 새로운 회피 방법을 찾지 못했다. 로컬에서 `gradlew.bat bootRun`으로 앱을 띄운 뒤(H2 인메모리, `local` 프로파일) `/`에서 직접 측정했다.

- `resize_window(375, 800)` 호출은 이전과 동일하게 "성공" 응답을 반환했지만, 직후 `javascript_tool`로 `window.innerWidth`/`innerHeight`를 직접 읽으면 여전히 `1920x855`로 변화가 없었다(1단계 재현: 성공 응답만 보고 실측을 생략했을 가능성을 배제하기 위해 이번에는 실측값을 직접 확인함 — 응답과 실제 렌더링이 일치하지 않는 것을 확인).
- `Ctrl+Shift+M`(기기 툴바 토글) 단축키를 `computer` 도구로 전송했으나 뷰포트에 변화가 없었다(`innerWidth` 재측정 결과 동일하게 1920 유지) — 이 자동화 환경에는 DevTools UI 자체가 노출되지 않는 것으로 보인다.
- OS 창 테두리를 드래그하는 방식은 시도하지 않았다: `computer` 도구의 좌표는 탭의 뷰포트 내부 기준이라 브라우저 창 테두리(OS 창 관리자 영역)를 가리킬 수 없어 이 접근 자체가 도구 설계상 불가능하다고 판단했다.
- CSS `zoom`/`devicePixelRatio` 조작으로 근접 시뮬레이션하는 방안은 시도하지 않았다: 이 값들은 레이아웃 뷰포트 크기나 미디어 쿼리 매칭에 영향을 주지 않아(브라우저의 실제 페이지 확대와 다름) 검증 목적에 부합하지 않는다고 판단해 시도 자체를 접었다.

결론적으로 이 자동화 세션에서는 실제 뷰포트를 바꿔 렌더링을 검증하는 방법이 없다. 1920px 고정 렌더링에서 콘솔 오류가 없음은 재확인했으나(§6 "확인함" 항목과 동일), 320~1440px 구간과 확대 배율은 여전히 사람이 직접 확인해야 한다. 위 "사용자가 직접 확인할 절차"는 그대로 유효하다.

**여전히 확인하지 않음 — 사람이 직접 확인 필요**
- **스크린리더 낭독**: NVDA(Windows) 또는 VoiceOver(macOS)로 위 7개 화면을 열고, 제목이 랜드마크로 낭독되는지, 목록·폼 라벨이 올바르게 읽히는지, 게시글/댓글 저장·삭제 시 `#app-toast`(`role="status"`, `aria-live="polite"`)와 게시글 등록·가입 등의 flash 메시지(`role="status"`)가 자동으로 낭독되는지, 가입 폼 오류 시 `aria-invalid` 필드가 오류로 안내되는지 직접 들어봐야 한다.
- **크로스 브라우저·실기기**: 데스크톱 Edge, 모바일 Safari(실제 iOS 기기 또는 macOS 시뮬레이터)에서 로그인 → 목록 → 상세 → 댓글 → 글쓰기 핵심 흐름을 한 번씩 수행해 Chrome과 동일하게 동작하는지 확인한다.
- **가로 방향 모바일, 가상 키보드 표시 상태**: 실제 모바일 기기에서 화면 회전과 텍스트 입력 시 가상 키보드가 올라온 상태의 레이아웃을 확인한다.

## 7. 최종 수용 기준

- [x] 사용자에게 보이는 모든 메뉴와 행동이 실제 경로·기능으로 연결된다.
- [x] 기존 데이터에 없는 검색·분류·숫자·순위·썸네일을 임의로 표시하지 않는다.
- [x] 확인한 화면(목록·상세·글쓰기·계정 폼)에서 320px부터 내용과 기능을 이용할 수 있다. 나머지 화면은 같은 `kraft-shell`·`kraft-main--account` 규칙을 공유하므로 구조적으로는 안전하나, 개별 320px 캡처로 전수 확인하지는 않았다.
- [x] 목록·글쓰기·상세·댓글·계정 화면이 같은 토큰과 컴포넌트 규칙을 사용한다(`style.css`의 `--kraft-*` 토큰, `.card-kraft`, `.flash`, `.btn-group-gap` 등 공통 클래스로 통일).
- [x] 본인·관리자만 관리 행동을 보고 API 권한 검사는 유지된다(화면 노출은 `OwnershipPolicy.canManage()`, API는 기존 `validateOwner()`로 같은 정책을 공유).
- [x] 현재 이메일 인증·가입·로그아웃 정책과 UI 안내가 일치한다(V6: 재발송 안내 문구 제거, 역할 갱신에 재로그인이 필요하다는 안내 추가).
- [x] 실패 시 입력을 유지하고 처리 중 중복 요청을 막는다(`aria-busy` + 버튼 비활성화를 저장·수정·댓글·삭제·업로드 전 구간에 적용).
- [ ] 키보드·확대·포커스·오류 메시지·색상 대비를 검증했다. **부분 완료** — 포커스 이동·오류 메시지·`aria-busy` 동적 반영·실측 색상 대비(13개 조합 전부 AA 통과)는 확인했으나, 400%/200% 확대와 스크린리더 낭독은 도구 제약과 사람의 확인이 필요해 여전히 남아 있다(§6 "후속 검증" 참고).
- [x] 변경에 필요한 테스트가 통과했다(`gradlew.bat test`, 매 단계 후 재확인). **부분 완료** — 실제 화면 캡처로 밀도·줄바꿈·겹침은 대표 뷰포트 위주로 확인했고, 320~1920px 전 구간 캡처 매트릭스나 Edge·Safari·실제 기기 검토는 남아 있다.

이번 1~5단계 구현·검증으로 Kraft 반응형 프론트엔드의 핵심 기능(공통 셸, 목록, 권한 기반 읽기·편집·댓글, 글쓰기·업로드, 계정 피드백)은 실제로 동작한다. 남은 항목(정밀 접근성 검증, 크로스 브라우저·실기기 확인, 전 구간 캡처)은 이 문서에 명시한 대로 후속 작업으로 추적한다.

## 8. Flyway 도입 (2026-09-11)

§3 "검색·분류·조회 수·추천·인기글·댓글 수" 구현으로 `posts.category`·`posts.view_count`·`post_likes` 테이블이 새로 필요해졌다. 운영(`ddl-auto: validate`)은 애플리케이션이 스키마를 바꾸지 않으므로, 이번 기회에 Flyway를 도입해 앞으로의 스키마 변경을 마이그레이션 파일로 관리하기로 했다.

- `build.gradle.kts`에 `spring-boot-flyway`, `flyway-mysql`(MariaDB용) 의존성을 추가했다.
- `src/main/resources/db/migration/V1__baseline.sql`이 이번 세션 이전까지의 스키마(users/posts/comments/email_verification_tokens)를, `V2__add_post_extras.sql`이 이번에 추가된 컬럼·테이블을 담는다.
- Flyway는 **운영(`application-prod.yml`)에서만 활성화**한다(`spring.flyway.enabled: true` + `baseline-on-migrate: true`, `baseline-version: "1"`). local(기본값)은 `application.yml`의 `spring.flyway.enabled: false`를 유지해 Hibernate가 엔티티 매핑으로 스키마를 직접 만든다(당시 `create-drop`, 2026-09-12부터 `update` — §15 참고) — 두 스키마 관리자가 같은 DB를 동시에 건드리는 상황(Flyway가 만든 테이블을 Hibernate가 `create-drop`으로 다시 지우는 등)을 피하기 위해서다.
- `baseline-on-migrate`는 이미 스키마가 있는 기존 운영 DB에 처음 연결할 때 `V1`을 실제로 실행하지 않고 "이미 적용됨"으로만 기록한 뒤 `V2`부터 진행하도록 한다. 완전히 새로 만드는 DB에서는 `V1`부터 그대로 실행된다.

**후속 검증(2026-09-11, local→Docker MariaDB 전환 이후)**: 아래 §9의 전환 이후 local 프로파일이 실제 Docker MariaDB에 붙으면서, Hibernate `create-drop`이 실제 MariaDB에 스키마를 만드는 것을 여러 차례 실측했다(회원가입·게시글 작성·이미지 교체·추천·댓글 있는 글 삭제까지 전체 기능 검증 포함). 이 과정에서 `role`/`category` 같은 `@Enumerated(STRING)` 필드가 **MariaDB에서도 H2와 마찬가지로 네이티브 `ENUM(...)` 타입으로 생성**되는 것을 boot 로그로 직접 확인했다(`category enum ('FREE','NOTICE','QNA') not null`) — 즉 H2 한정 현상이 아니었다. 반면 `V1`/`V2` 마이그레이션 SQL은 이식성을 위해 `VARCHAR`로 작성해뒀다. **이때 남겨둔 미검증 항목**(Flyway가 local에서 비활성이라 `V1`/`V2`가 한 번도 실행된 적이 없다는 점)은 2026-09-12에 실제로 검증했고, 그 결과 배포를 막는 결함 2건이 드러났다 — 아래 §11 참고.

## 9. local 프로파일을 Docker MariaDB로 전환 (2026-09-11)

지금까지 `local`(기본값, H2 인메모리)과 `docker`(수동으로 `--spring.profiles.active=docker` 지정, 실제 MariaDB) 두 프로파일이 따로 있었다. 로컬에서 항상 실제 MariaDB로 개발·확인하길 원해서, 이 둘을 하나로 합쳤다.

- `src/main/resources/application-local.yml`을 H2 설정에서 `application-docker.yml`이 쓰던 MariaDB(docker-compose) 접속 설정으로 바꿨다. `application-docker.yml`은 삭제했다 — 이제 프로파일을 따로 지정할 필요 없이 `./gradlew bootRun`만 실행하면(기본 프로파일이 `local`이므로) Docker MariaDB에 붙는다.
- `gradlew test`가 계속 빠르고 격리된 H2로 돌게 하려고, 별도의 **`test` 스프링 프로파일**을 만들었다: `src/test/resources/application-test.yml`에 옛 H2 설정을 그대로 옮기고, `build.gradle.kts`의 `Test` 태스크에 `systemProperty("spring.profiles.active", "test")`를 추가했다 — `local`(기본값)의 자리를 `test`가 대신하도록 명시적으로 지정하는 방식이다(처음에는 같은 이름의 파일을 test/resources에 둬 클래스패스 우선순위로 덮어쓰는 방법을 썼으나, 파일명을 `application-test.yml`로 분리하면서 이 방식으로 바꿨다 — 이름이 다르면 프로파일을 실제로 활성화해야 로딩되기 때문이다). `@DataJpaTest` 슬라이스(예: `PostRepositoryTest`)는 기본적으로 임베디드 DB로 자동 교체되어 이 설정과 무관하게 이미 H2로 돌고 있었고, `@SpringBootTest`(`KraftApplicationTests`, `SecurityConfigTest`)가 실제 이 설정의 적용 대상이다. 그 결과 `gradlew test`는 Docker가 떠 있지 않아도 항상 통과한다.
- 실행 순서는 이전 `docker` 프로파일과 같다: `docker compose up -d` → `.env` 값을 OS 환경변수로 로드 → `./gradlew bootRun`(프로파일 지정 불필요). `.env.example`에 절차를 정리해 남겼다.
- `EmailSender`/`SmtpEmailSender`의 주석과 `application.yml`의 Flyway 관련 주석에서 "docker 프로파일" 언급을 제거하고 "local(기본값)"로 통일했다.

**검증**: `gradlew.bat test` 전체 통과(H2 기준, Docker 없이도 성공). `docker compose down -v` → `up -d`로 완전히 새 볼륨을 만든 뒤 `./gradlew bootRun`(프로파일 지정 없이)으로 기동해 실제 MariaDB에 스키마가 만들어지고, 회원가입·이메일 인증·게시글 작성까지 정상 동작함을 확인했다.

## 10. 로그 파일 분리 (logback-spring.xml, 2026-09-11)

이메일 발송 문제를 조사하는 과정에서 콘솔 로그만으로는 실행이 끝나면 사라지고, SQL·보안·이메일 로그가 한데 뒤섞여 원인을 찾기 번거로웠다. `src/main/resources/logback-spring.xml`을 새로 추가해 로그를 파일로 남기고 성격별로 나눴다.

- `logs/kraft.log` — 전체 애플리케이션 로그(콘솔과 동일, INFO 이상). Hibernate SQL은 여기서 뺐다(아래).
- `logs/kraft-error.log` — ERROR만 모은다(어떤 로거에서 나왔든). 장애를 빠르게 훑어볼 때 쓴다.
- `logs/kraft-sql.log` — `org.hibernate.SQL`/`org.hibernate.orm.jdbc.bind`. `additivity="false"`로 둬서 `kraft.log`에는 안 섞이고 콘솔·이 파일에만 남는다(레벨은 각 `application-*.yml`의 `logging.level.org.hibernate.SQL`이 그대로 결정 — XML은 등급이 아니라 "어디로 보낼지"만 담당).
- `logs/kraft-security.log` — `org.springframework.security`. `kraft.log`에도 그대로 남고 이 파일에도 따로 모인다.
- `logs/kraft-email.log` — `com.kraft.service.user`(인증 토큰 발급·재발송, 실제 SMTP 발송). 이번 세션에 이메일 문제를 여러 번 다룬 것을 계기로 추가했다.

날짜별·20MB 초과 시 회전해 `logs/archive/*.log.gz`로 압축 보관한다(`kraft-error.log`는 90일, 나머지는 14~30일 보관). `logs/`는 `.gitignore`에 등록했다(`uploads/`와 같은 패턴).

**검증**: `gradlew.bat test`로 5개 파일이 모두 만들어지고 내용이 의도대로 나뉘는 것을 확인했다(`kraft-sql.log`에 Hibernate DEBUG, `kraft.log`에는 SQL이 섞이지 않음, `kraft-email.log`에 재발송 테스트의 경고+스택트레이스, `kraft-error.log`는 실패 없어 빈 파일). 실제 `bootRun`으로도 동일하게 분리되는 것을 재확인했다.

## 11. Flyway 마이그레이션 정합성 결함 2건 수정 + 실증 (2026-09-12)

§8의 미검증 항목(“`V1`/`V2`가 실제로 실행된 적이 없다”)을 확인한 결과, 단순 미검증이 아니라 **운영 배포를 실제로 막을 결함 2건**이었다. 둘 다 수정하고, 운영과 동일한 스키마 경로(Flyway + `validate` + `session.jdbc.initialize-schema: never`)로 실제 Docker MariaDB에 기동해 실측했다.

### 결함 1 — `ddl-auto: validate` 기동 실패 (ENUM vs VARCHAR)

Hibernate는 MariaDB에서 `@Enumerated(STRING)` 필드를 네이티브 `enum`으로 만들고, `validate`도 그 타입을 기대한다(`logs/kraft-sql.log`: `role enum ('ADMIN','GUEST','USER') not null`). 반면 `V1`/`V2`는 `VARCHAR(20)`이었다.

- 처음에는 **엔티티 쪽을 VARCHAR로 고정**(`@JdbcTypeCode(SqlTypes.VARCHAR)`)하려 했으나, 그러면 Hibernate가 `check (role in (...))` 제약을 함께 만들고 H2가 이를 평가하지 못해 `@DataJpaTest` 20여 개가 `DataIntegrityViolationException: Check constraint invalid` (H2 23514)로 깨졌다. Hibernate 7에는 이 체크 제약 생성을 끄는 설정도, 전역 enum JDBC 타입 설정(`preferred_enum_jdbc_type`, 7에서 제거)도 없다.
- 따라서 **마이그레이션 쪽을 Hibernate 생성물에 맞추는 폴백**을 택했다: `V1`의 `users.role`, `V2`의 `posts.category`를 `ENUM('ADMIN','GUEST','USER')` / `ENUM('FREE','NOTICE','QNA')`로 바꿨다. 값 목록은 Hibernate가 만드는 것과 동일하게 **선언 순서가 아닌 알파벳 순**이어야 한다.

### 결함 2 — 운영에 `SPRING_SESSION` 테이블을 만드는 주체가 없음 (조용한 런타임 실패)

`application.yml`이 모든 프로파일에 `spring.session.store-type: jdbc`를 강제하는데, 운영은 `initialize-schema: never`이고 `db/migration/`에도 세션 테이블이 없었다. 세션 테이블은 JPA 엔티티가 아니라 `validate`가 잡지 못한다 — **기동은 성공하고 첫 로그인에서 터진다.** `db/migration/V3__spring_session.sql`을 추가해 해결했다. `IF NOT EXISTS`를 유지해 과거 배포에서 Spring Session이 이미 만들어 둔 DB에서도 안전하다. local은 계속 `session/schema-mariadb.sql`을 쓰므로(local은 Flyway 비활성) **두 파일은 같은 내용으로 유지해야 하며**, 양쪽 주석에 상호 참조를 남겼다. 유일한 차이는 V3에서 PRIMARY KEY 제약의 이름을 뺀 것이다(MariaDB가 이름을 무시하면서 WARN 1280을 남겨서).

### 실증 결과

운영 설정을 건드리지 않고 명령행 오버라이드로 재현했다.

```powershell
.\gradlew.bat bootRun --args="--spring.flyway.enabled=true --spring.jpa.hibernate.ddl-auto=validate --spring.session.jdbc.initialize-schema=never"
```

**시나리오 A — 새 운영 DB (`docker compose down -v` → `up -d`): 통과**

- `Successfully applied 3 migrations to schema kraft, now at version v3`, `Initialized JPA EntityManagerFactory`(= `validate` 통과), `Started KraftApplication in 3.6s`.
- 기능 확인: 회원가입 → 인증 링크 클릭(`role`이 `GUEST`→`USER`) → **로그인 성공(302 → `/`)** → 게시글 작성 성공.
- `SPRING_SESSION`에 `PRINCIPAL_NAME`이 채워진 세션 행이 남았다(결함 2의 실제 검증 기준). `logs/kraft-error.log` 0줄, `logs/kraft.log` WARN 0건.

**시나리오 B — 기존 DB (`baseline-on-migrate` 경로): 예상대로 실패, 처방까지 실측**

Hibernate `create-drop`으로 현재 스키마를 만든 DB(= `flyway_schema_history` 없음)에 위 명령으로 기동했다.

1. 오버라이드만으로는 재현되지 않는다 — `baseline-on-migrate`/`baseline-version`은 `application-prod.yml`에만 있어 local 기동에는 적용되지 않는다. 이때는 `FlywayException: Found non-empty schema(s) 'kraft' but no schema history table.`로 기동 실패한다. 운영 경로를 재현하려면 `--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=1`을 함께 줘야 한다.
2. 운영과 같은 `baseline-version=1`로 기동하면 V1을 건너뛰고 V2부터 적용하는데, 이미 컬럼이 있으므로 실패한다:
   ```
   Successfully baselined schema with version: 1
   Migrating schema `kraft` to version "2 - add post extras"
   SQL State  : 42S21 / Error Code : 1060
   Message    : Duplicate column name 'category'
   Location   : db/migration/V2__add_post_extras.sql  Line: 7
   ```
   `flyway_schema_history`에는 baseline 1행 + `success=0`인 V2 행이 남는다(복구하려면 실패 행을 지우거나 `flyway repair`).
3. 같은 DB에 **실제 상태에 맞는 `baseline-version=3`**으로 다시 기동하면 정상이다: `Successfully baselined schema with version: 3` → `Schema is up to date. No migration necessary.` → `Started KraftApplication`.

즉 결함이 아니라 **대상 DB의 실제 상태에 맞는 `baseline-version`을 골라야 한다는 배포 절차 문제**다.

### 배포 전 점검 절차 (재발 방지)

1. **대상 DB 상태 판별** — `SHOW TABLES;`로 (a) 완전히 빈 DB인지, (b) `flyway_schema_history`가 있는지, (c) `posts`에 `category`/`view_count`가 있고 `post_likes`·`SPRING_SESSION`이 있는지 확인한다.
2. **`baseline-version` 결정** — 빈 DB면 설정을 그대로 두면 된다(V1부터 전부 실행). 기존 DB면 실제 상태에 맞춘다: §3 기능 이전 스키마면 `1`, `category`/`view_count`/`post_likes`까지 있으면 `2`, 세션 테이블까지 있으면 `3`. `application-prod.yml`의 `baseline-version: "1"`은 **빈 DB나 §3 이전 스키마를 가정한 값**이므로 그대로 배포하지 말 것.
3. **리허설** — 운영 데이터의 사본(또는 같은 상태로 만든 DB)에 위 `bootRun` 오버라이드 명령을 한 번 돌려 Flyway 적용과 `validate` 통과를 확인한다. 마이그레이션을 새로 추가할 때마다 반복한다.
4. **기동 성공만으로는 부족하다** — 세션 테이블 문제는 `validate`가 잡지 못하고 **첫 로그인에서만 드러난다.** 배포 후 스모크 테스트에 반드시 **로그인 1회**를 포함하고, `SELECT COUNT(*) FROM SPRING_SESSION;`으로 행이 생기는지 확인한다.
5. **`@Enumerated(STRING)` 필드를 추가하거나 enum 값을 바꿀 때** — 마이그레이션 SQL의 `ENUM(...)` 값 목록을 Hibernate가 생성하는 것과 똑같이 **알파벳 순**으로 맞춘다. 확인은 local 기동 후 `logs/kraft-sql.log`의 `create table` 문을 보면 된다.

**정리**: 검증 후 `docker compose down -v` → `up -d`로 볼륨을 비워 평소 local 개발 상태로 되돌렸다.

## 12. 아이폰 사진(HEIC) 업로드 처리 (2026-09-12)

§6에 남아 있는 "크로스 브라우저·실기기 확인" 중 **iOS Safari에서 실제로 깨질 가능성이 가장 큰 지점**을 선제적으로 처리했다. `PostImageService`의 허용 확장자는 `jpg/jpeg/png/gif/webp`인데, 아이폰의 기본 촬영 포맷은 HEIC라 사진 보관함에서 고른 파일이 그대로 도착하면 `허용되지 않는 파일 형식입니다: heic`로 거부되고, 사용자는 무엇을 해야 할지 알 수 없었다.

**HEIC를 허용 목록에 넣지 않은 이유**: 받는 것 자체는 가능하지만 **Chrome·Firefox·Edge가 HEIC를 렌더링하지 못한다**(Safari만 표시 가능). 허용하면 업로드는 성공하고 다른 방문자 화면에서만 깨진 이미지가 되어 더 나쁘다. 서버에서 JPG로 변환하려면 JDK에 없는 네이티브 디코더(libheif 등)가 필요해 이번 범위를 넘는다. 그래서 **명확히 거부하되 해결 방법을 안내**하는 쪽을 택했다.

- `PostImageService`에 HEIC/HEIF 전용 분기와 안내 문구를 추가했다(아이폰 [설정] > [카메라] > [포맷]을 '높은 호환성'으로).
- **확장자만 믿지 않는다** — 공유·복사 과정에서 이름만 `.jpg`로 바뀐 HEIC가 실제로 생긴다. ISO base media file format 헤더(4~8바이트 `ftyp` + 브랜드 `heic`/`mif1` 등)를 읽어 내용으로도 판별하고 같은 안내로 응답한다. 헤더보다 짧거나 읽지 못하면 판단하지 않고 기존 확장자 검사로 넘어간다.
- `index.js`의 클라이언트 사전 검사에도 같은 분기를 넣어 왕복 없이 바로 안내한다(판정은 여전히 서버가 내린다).
- **함께 고친 결함**: 검증은 확장자를 소문자로 바꿔 비교했지만 저장은 원본 확장자를 그대로 썼다. 휴대폰·카메라가 흔히 만드는 `IMG_0001.JPG`가 `uuid.JPG`로 저장돼 대소문자를 구분하는 파일 시스템에서 문제가 될 수 있었다. 정규화한 확장자를 저장까지 쓰도록 통일했다.

**검증**: `PostImageServiceTest` 15건 통과(신규 5건 — 대문자 확장자, `.heic` 확장자, 이름만 `.jpg`인 HEIC, HEIF가 아닌 `ftyp` 파일, 헤더보다 짧은 파일). 실제 기동 후 `POST /api/v1/posts/images`로 3종을 올려 실측했다: 이름만 `.jpg`인 HEIC → 400 + 안내 문구, `.heic` → 400 + 안내 문구, `IMG_0001.JPG` → 200 + `/images/<uuid>.jpg`(소문자).

**남은 크로스 브라우저 검증**(여전히 사람이 직접 해야 함): Edge에서 핵심 흐름 1회(Chromium이라 리스크 낮음), iOS Safari 실기기에서 `position: sticky`(`style.css:270` `.kraft-nav`) 동작, Bootstrap 4.3.1 모달의 body 스크롤, `type="search"` 렌더링. 실기기 접속은 PC의 LAN IP로 8080을 열고 `APP_BASE_URL`을 그 주소로 지정해야 인증 메일 링크가 폰에서 열린다.

## 13. 지원 브라우저 범위 확정 (2026-09-12)

§12에서 남긴 크로스 브라우저 항목을 판단하려면 하한선이 필요해, **데스크톱 Chrome·Edge 최신 2개 + Safari 15+, 모바일 iOS 15+ / Android Chrome 최신 2개**로 정했다(표와 근거는 [03. 반응형 화면 명세](03-responsive-ui-spec.md) "지원 브라우저 범위").

이 결정으로 **하지 않기로 한 것**과 **해야 했던 것**:

- **flex `gap` 폴백은 넣지 않는다** — Safari는 14.1부터 지원하므로 iOS 15는 기준을 넘는다. `style.css`의 20곳을 그대로 둔다. 이것이 이번 결정의 가장 큰 실익이다(폴백을 넣었다면 20곳에 마진 규칙이 중복될 뻔했다).
- **`:focus-visible`만 범위에 어긋났다** — Safari는 **15.4**부터 지원해 iOS 15.0~15.3에서는 규칙이 통째로 무시되고 **키보드 포커스 표시가 사라진다.** `:focus`로 먼저 깔고 `:focus:not(:focus-visible)`로 마우스 포커스의 테두리만 걷어내는 방식으로 고쳤다 — `:focus-visible`을 모르는 브라우저는 두 번째 규칙도 해석하지 못해 `:focus` 표시를 그대로 유지한다.
- **그 밖에 범위를 벗어나는 기능은 없다**(실측 스캔): CSS는 `:has()`·컨테이너 쿼리·`dvh`·`aspect-ratio`·`accent-color`·`subgrid`를 쓰지 않고, JS는 ES5 + jQuery 3.3.1이라 화살표 함수·`const`/`let`·`fetch`·옵셔널 체이닝이 없다(최신 API는 `Element.closest()`와 `FormData`뿐). CSS·JS를 정적 파일로 그대로 서빙해 빌드 단계가 없으므로, **문서에 적은 범위가 곧 실제 하한**이다.

**검증**: 실제 기동한 화면에서 Tab 이동 시 `:focus-visible`이 걸리고 `outline: solid 2px rgb(79, 70, 229)`가 계산값으로 적용되는 것을, 키보드가 아닌 경로의 포커스에서는 `outline`이 제거되는 것을 브라우저에서 확인했다.

## 14. 이동·계정 버튼을 헤더 오른쪽으로 통합 (2026-09-12)

지금까지 같은 행동 버튼이 세 곳에 흩어져 있었다: 왼쪽 열 탐색 패널(`layout/navbar :: sitenav`), 목록 화면 제목 옆 "글쓰기", 목록 오른쪽 안내 영역의 "로그인·회원가입". **모든 이동·계정 버튼을 헤더 오른쪽 한 곳으로 모으고 나머지는 제거했다.** "전체 게시글" 항목은 삭제했다 — 헤더 왼쪽 브랜드(`Kraft`)가 이미 `/`로 가므로 같은 목적지가 둘이었다.

- `layout/navbar.html` — `sitenav` fragment를 삭제하고 `topbar` 안에 `.kraft-actions`(이메일 · 글쓰기 · 인증 메일 재발송 · 로그인 · 회원가입 · 비밀번호 변경 · 로그아웃)를 넣었다. 8개 템플릿에서 `sitenav` 포함 줄을 모두 제거했다.
- **좁은 화면 처리는 그대로 유지** — 768px 미만에서는 기존 "메뉴" 토글이 헤더 아래로 버튼 목록을 펼치고(`hidden` 속성으로 닫힘을 표현해 닫혀 있을 때 탭 순서에서 빠진다), 768px 이상에서는 CSS가 가로 한 줄로 펼치고 토글을 숨긴다. 토글이 숨는 분기점을 992px에서 768px로 내렸다(버튼이 한 줄에 들어가는 구간이 넓어져서).
- **셸 그리드에서 왼쪽 192px 열을 제거**했다. 본문은 항상 한 열이고, 목록 화면만 1200px 이상에서 오른쪽 안내 248px를 더 쓴다.
- **본문에 남아 있던 중복 버튼을 모든 페이지에서 제거**했다: 목록의 "글쓰기"와 빈 상태의 "첫 글 작성하기"/"로그인하고 글쓰기"(안내 문구로 대체), 안내 영역의 "로그인·회원가입", 글쓰기·비밀번호 변경 화면의 "로그인하러 가기"·"목록으로", 인증 결과 화면의 "로그인하러 가기", 없는 게시글 화면의 "목록으로 이동", 게시글 읽기 화면의 "← 목록으로". 쓰이지 않게 된 `.kraft-nav*`·`.kraft-aside__actions`·`.back-link` CSS도 지웠다.
- 폼 자체의 취소·저장 버튼과 페이저는 이동·계정 버튼이 아니므로 본문에 그대로 둔다.

**검증**: `gradlew.bat test` 전체 통과. 실제 기동해 익명·로그인 두 상태에서 8개 화면(`/`, `/login`, `/signup`, `/posts/save`, `/users/me/password`, `/users/verify`, 게시글 읽기, 없는 게시글)이 모두 200으로 렌더링되고 헤더 오른쪽에 상태에 맞는 버튼만 나오는 것을 확인했다(현재 화면 항목은 `is-current`로 강조). 토글 버튼의 열림·닫힘이 `hidden`/`aria-expanded`를 정확히 뒤집는 것도 확인했다. 320~767px 실제 렌더링은 이 세션의 자동화 도구가 뷰포트를 1920px로 고정하는 제약(§6) 때문에 여전히 사람이 직접 봐야 한다.

## 15. local의 DB 데이터를 기동 사이에 유지 (2026-09-12)

local이 `ddl-auto: create-drop`이라 **`./gradlew bootRun`을 다시 할 때마다 계정·게시글이 전부 사라졌다.** Docker 볼륨(`kraft-mariadb-data`)은 데이터를 잘 들고 있었고, 앱이 기동할 때 스스로 전체 drop·재생성을 한 것이 원인이다(컨테이너 재시작이나 `docker compose down`(볼륨 유지)으로는 사라지지 않았다).

`application-local.yml`의 `ddl-auto`를 **`update`**로 바꿨다. 테이블·컬럼 추가는 계속 자동 반영하면서 기존 데이터는 남는다.

- **`update`의 한계**: 컬럼·테이블 삭제, 타입 변경, 제약 변경은 반영하지 않는다. 엔티티에서 필드를 지우거나 타입을 바꿨다면 `docker compose down -v && docker compose up -d`로 스키마를 한 번 비워야 실제와 맞는다. README와 `application-local.yml` 주석에 이 절차를 적어뒀다.
- **테스트는 그대로 `create-drop`** — `src/test/resources/application-test.yml`(H2 인메모리)은 매번 깨끗한 상태가 맞으므로 건드리지 않았다.
- **운영은 영향 없다** — prod는 `validate` + Flyway이며, 운영 스키마의 정합성은 이 설정이 아니라 `db/migration`이 책임진다(§11의 배포 전 점검 절차).

**검증**: 볼륨을 비우고(`down -v` → `up -d`) 기동해 회원가입 → 이메일 인증 → 로그인 → 게시글 작성까지 한 뒤, **앱을 정상 종료하고 다시 띄웠다.** 재기동 후에도 `users`(role=USER)와 `posts`가 그대로 남아 있고 목록 화면에도 그 글이 보였다. 재기동 로그 구간에 `drop table` 0건, WARN 0건, ERROR 0건.

## 16. 인증 메일 재발송·비밀번호 변경을 모달로, 헤더에는 닉네임 표시 (2026-09-12)

### 모달 전환

두 행동 모두 **현재 화면을 떠나지 않고** 처리하도록 바꿨다. 모달은 `layout/footer :: footer`에 두어 모든 화면이 공유한다(헤더 버튼과 같은 위치 전략).

- **비밀번호 변경** — 별도 화면(`/users/me/password`)과 `user/change-password.html`, `IndexController`의 매핑을 삭제하고 `#changePasswordModal`로 옮겼다. 화면 이동이 없으므로 오류는 flash(이동 후 1회 표시)가 아니라 모달 안(`#change-password-error`)에 바로 띄운다. 모달을 열 때마다 입력과 오류를 초기화하고 첫 입력에 포커스를 준다.
- **인증 메일 재발송** — 누르는 즉시 메일이 나가던 것을 `#resendVerificationModal`에서 한 번 확인받도록 했다. 재발송은 이전 토큰을 무효로 만들기 때문이다. 결과는 기존대로 토스트로 알리고 모달은 닫는다.
- **로그아웃 복귀 규칙 변경** — "비밀번호 변경 후에는 로그인 화면으로"라는 기존 정책(§3)은 지금까지 Referer가 `/users/me/password`인지로 판별했는데, 모달은 어느 화면에서나 열리므로 Referer로는 구분할 수 없다. 로그아웃 폼에 `next` 히든 필드를 추가하고(`layout/footer.html`), 변경 성공 시에만 JS가 `/login`을 채운다. `SecurityConfig`의 로그아웃 성공 처리는 `next`가 `/`로 시작하고 `//`(외부 도메인)로 시작하지 않을 때만 신뢰한다 — 로그인 성공 후 복귀와 같은 규칙이다. `SecurityConfigTest`에 `next` 우선 적용·외부 URL 무시·빈 값 폴백 3건을 추가했다.

### 헤더에 이메일 대신 닉네임

`sec:authentication="name"`은 로그인 아이디(=이메일)라서, 화면 표시용 이름을 담는 `KraftUserDetails`(`displayName`)를 만들고 `UserDetailsServiceImpl`이 이를 반환하도록 했다. **`getUsername()`은 이메일 그대로 둔다** — 서비스 계층이 `authentication.getName()`으로 회원을 조회하기 때문이다. 글쓰기 화면의 "작성자" 칸도 같은 값으로 바꿨다(목록의 작성자 표시와 이제 일치한다).

**실제로 밟은 함정**: 처음에는 템플릿에서 `principal.displayName`을 바로 참조했는데, **세션이 DB(`SPRING_SESSION`)에 저장되어 재기동·배포 뒤에도 살아남기 때문에** 변경 이전에 만들어진 세션에는 `displayName`이 없는 옛 principal이 그대로 들어 있었다. 그 세션으로 접속하면 헤더를 쓰는 **모든 화면이 500**으로 깨졌다(`NotReadablePropertyException`, 실제 재현). principal 타입을 확인하고 아니면 이메일로 물러서도록 고쳤다. 운영 배포에서도 똑같이 발생할 상황이라 템플릿 주석에 이유를 남겼다.

**검증**: `gradlew.bat test` 전체 통과(166건). 실제 기동해 브라우저로 확인했다 — 헤더에 닉네임(`Modal Tester`) 표시, 비밀번호 변경 모달에서 현재 비밀번호를 틀리면 모달 안에 "현재 비밀번호가 일치하지 않습니다."가 뜨고 모달이 닫히지 않으며, 올바르게 입력하면 로그아웃되고 `/login`으로 이동해 "비밀번호가 변경되었습니다. 다시 로그인해 주세요." 안내가 뜬다(바뀐 비밀번호로 로그인되는 것까지 확인). 재발송 모달은 확인 후 토스트가 뜨고 모달이 닫힌다. 옛 principal이 담긴 세션으로도 화면이 200으로 렌더링되는 것을 확인했다.
