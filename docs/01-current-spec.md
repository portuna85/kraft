# 01. 현재 프론트엔드 스펙

기준: `7913255`, 2026-09-10. 코드로 확인한 동작을 기록한다. 목표 화면은 [반응형 화면 명세](03-responsive-ui-spec.md)에서 정의한다.

## 1. 기술 구성

| 항목 | 현재 상태 | 근거 |
| --- | --- | --- |
| 서버 | Java 25, Spring Boot 4.1.1, Spring MVC | [build.gradle.kts](../build.gradle.kts) |
| 렌더링 | Thymeleaf, Spring Security 템플릿 연동 | [build.gradle.kts](../build.gradle.kts) |
| 스타일 | Bootstrap 4.3.1 CDN + 자체 `style.css` | [header.html](../src/main/resources/templates/layout/header.html) |
| 브라우저 동작 | jQuery 3.3.1, Bootstrap 4.3.1 JS, 자체 `index.js` | [footer.html](../src/main/resources/templates/layout/footer.html) |
| 인증 | 이메일을 사용자 이름으로 쓰는 폼 로그인, 서버 세션, CSRF | [SecurityConfig.java](../src/main/java/com/kraft/config/security/SecurityConfig.java), [UserDetailsServiceImpl.java](../src/main/java/com/kraft/config/security/UserDetailsServiceImpl.java) |
| 저장 | JPA, H2·MariaDB 드라이버, JDBC 세션 | [build.gradle.kts](../build.gradle.kts), [application.yml](../src/main/resources/application.yml) |
| 이미지 | 로컬 업로드 후 `/images/**` URL로 표시 | [PostImageService.java](../src/main/java/com/kraft/service/post/PostImageService.java) |

현재 CSS에는 인디고 `#4f46e5`, 흰색 표면, 회색 배경, 카드·폼·목록·토스트 스타일이 있다. `Pretendard`는 폰트 목록에만 있으며 별도의 폰트 파일이나 다운로드 선언은 확인되지 않는다. 전용 미디어 쿼리는 없다. Bootstrap 기본 반응형 기능과 `viewport` 메타 태그를 사용한다.

## 2. 화면과 경로

화면 경로는 [IndexController.java](../src/main/java/com/kraft/web/IndexController.java)에 정의되어 있다.

| 경로 | 템플릿 | 현재 화면 동작 |
| --- | --- | --- |
| `/`, `/?page=n` | [index.html](../src/main/resources/templates/index.html) | 게시글 번호·제목·작성자·최종 수정일 표, 페이지 이동 |
| `/posts/save` | [post-save.html](../src/main/resources/templates/post/post-save.html) | 제목·본문·이미지 한 개로 게시글 등록 |
| `/posts/update/{id}` | [post-update.html](../src/main/resources/templates/post/post-update.html) | 게시글 조회와 수정 폼이 한 화면에 존재, 이미지 표시, 댓글 목록·등록·삭제 |
| `/signup` | [signup.html](../src/main/resources/templates/user/signup.html) | 이름·이메일·비밀번호·비밀번호 확인 |
| `/login` | [login.html](../src/main/resources/templates/user/login.html) | 이메일·비밀번호 입력, 로그인 실패·로그아웃 메시지 |
| `/users/me/password` | [change-password.html](../src/main/resources/templates/user/change-password.html) | 현재 비밀번호·새 비밀번호 입력 |
| `/users/verify?token=...` | [verify-result.html](../src/main/resources/templates/user/verify-result.html) | 인증 링크 처리 결과와 로그인 이동 |

공통 화면은 `layout/header`, `layout/navbar`, `layout/footer`로 조립한다. `footer`는 로그아웃 폼, 토스트, 댓글 삭제 모달, 스크립트를 제공하며 현재 사이트 소개용 푸터는 없다.

현재 페이지 번호는 요청·응답에서 0부터 시작하고 화면에서는 1부터 표시한다. 기본 페이지 크기는 10개다. 조회는 `id DESC` 순서이며 최종 수정일 기준 정렬이 아니다. 모든 페이지 번호를 출력하므로 페이지가 많을 때 화면 폭을 초과할 수 있다. 근거: [PostRepository.java](../src/main/java/com/kraft/domain/post/PostRepository.java), [PostsPageResponseDto.java](../src/main/java/com/kraft/web/dto/post/PostsPageResponseDto.java).

## 3. 제공되는 API

아래 경로와 페이로드는 기존 계약이다. 디자인 변경에 맞춰 임의로 경로를 바꾸지 않는다.

| 기능 | 요청 | 본문 / 응답 |
| --- | --- | --- |
| 게시글 목록 | `GET /api/v1/posts?page=0&size=10` | 페이지 응답 |
| 게시글 조회 | `GET /api/v1/posts/{id}` | 게시글 상세 응답 |
| 게시글 등록 | `POST /api/v1/posts` | `{title, content, picture}` → ID |
| 게시글 수정 | `PUT /api/v1/posts/{id}` | `{title, content}` → ID |
| 게시글 삭제 | `DELETE /api/v1/posts/{id}` | 삭제한 ID |
| 이미지 업로드 | `POST /api/v1/posts/images` | multipart `file` → `{url}` |
| 댓글 목록 | `GET /api/v1/posts/{postId}/comments` | 댓글 배열 |
| 댓글 등록 | `POST /api/v1/posts/{postId}/comments` | `{content}` → ID |
| 댓글 수정 | `PUT /api/v1/comments/{id}` | `{content}` → ID |
| 댓글 삭제 | `DELETE /api/v1/comments/{id}` | 삭제한 ID |
| 회원가입 | `POST /api/v1/users` | `{name, email, password}` → ID |
| 비밀번호 변경 | `PUT /api/v1/users/me/password` | `{currentPassword, newPassword}` → 204 |
| 로그인 | `POST /login` | 폼 `username`, `password`, CSRF → 리다이렉트 |
| 로그아웃 | `POST /logout` | CSRF 포함 폼 → 리다이렉트 |

근거: [PostApiController](../src/main/java/com/kraft/web/api/PostApiController.java), [CommentApiController](../src/main/java/com/kraft/web/api/CommentApiController.java), [UserApiController](../src/main/java/com/kraft/web/api/UserApiController.java), [SecurityConfig](../src/main/java/com/kraft/config/security/SecurityConfig.java).

## 4. 화면에 사용할 수 있는 데이터

| 데이터 | 현재 필드 | 설계에 미치는 제약 |
| --- | --- | --- |
| 게시글 목록 항목 | `id`, `title`, `author`, `modifiedDate` | 썸네일·댓글 수·추천 수·조회 수·분류 없음 |
| 게시글 페이지 | `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last` | 전체 게시글 수와 페이지 이동 표시 가능 |
| 게시글 상세 | `id`, `title`, `content`, `picture`, `author` | 작성·수정 시각, 작성자 ID, 관리 권한 필드 없음 |
| 댓글 | `id`, `postId`, `content`, `author`, `createdAt` | 작성자 ID와 관리 권한 필드 없음. 상세에 로드된 배열의 개수는 표시 가능 |
| 로그인 사용자 | `Authentication.name`은 이메일, authorities는 역할 | 화면의 작성자 이름과 이메일을 비교하여 소유권을 판단하면 안 됨 |

근거: [게시글 DTO](../src/main/java/com/kraft/web/dto/post), [댓글 DTO](../src/main/java/com/kraft/web/dto/comment), [UserDetailsServiceImpl](../src/main/java/com/kraft/config/security/UserDetailsServiceImpl.java).

목록 조회마다 댓글 API를 추가 호출해 댓글 수를 채우는 설계는 채택하지 않는다. 목록에서 없는 값은 표시하지 않는다. 댓글은 현재 한 게시글의 전체 목록을 오래된 ID 순서로 조회하며 페이지 API가 없다. 근거: [CommentRepository.java](../src/main/java/com/kraft/domain/comment/CommentRepository.java).

## 5. 입력과 업로드 규칙

| 입력 | 현재 검증 | 목표 UI에서 주의할 점 |
| --- | --- | --- |
| 게시글 제목·본문 | `@NotBlank` | 필수값 표시. 제목 저장 컬럼은 255자이므로 `maxlength=255` 안내를 추가하되 API 길이 검증은 별도 보완 항목 |
| 댓글 본문 | `@NotBlank` | 임의의 서버 글자 수 제한을 안내하지 않음 |
| 가입 이름 | 필수, 서비스에서 중복 확인 | 컬럼 길이 50자. 클라이언트 안내와 서버 길이 검증을 구분 |
| 가입 이메일 | 필수, 이메일 형식, 서비스에서 중복 확인 | 별도 중복확인 API 없음. 제출 결과로 안내 |
| 가입 비밀번호 | 8자 이상, 대문자·소문자·특수문자 각각 포함 | 숫자 포함은 현재 필수 조건이 아님 |
| 비밀번호 확인 | 브라우저에서 일치 여부 확인 | 가입 API의 필드가 아님 |
| 새 비밀번호 | 필수, 8자 이상 | 현재 변경 API는 가입 API와 같은 복잡도 규칙을 적용하지 않음 |
| 첨부 이미지 | 한 개, 최대 `5 × 1024 × 1024` bytes, `jpg/jpeg/png/gif/webp` 확장자 | 허용 형식과 용량을 입력 옆에 표시. 브라우저 `accept`만으로 검증 완료 처리 금지 |

5MB는 서비스 코드의 검사 기준이다. 저장소의 `application*.yml`에는 multipart 최대 파일·요청 크기 설정이 명시되어 있지 않다. 실제 수신 제한이 서비스보다 작을 가능성이 있으므로 실행 환경의 multipart·프록시 제한을 확인하기 전에는 5MB 파일의 업로드 성공이 검증되었다고 간주하지 않는다.

이미지 업로드와 게시글 저장은 별도 요청이다. 업로드 성공 후 글 저장이 실패할 수 있으며, 현재 업로드 파일 삭제 API는 없다. 수정 API에는 `picture`가 없으므로 기존 게시글의 이미지 교체·삭제 UI는 이번 범위에 넣지 않는다.

근거: [요청 DTO](../src/main/java/com/kraft/web/dto), [Post.java](../src/main/java/com/kraft/domain/post/Post.java), [User.java](../src/main/java/com/kraft/domain/user/User.java), [PostImageService.java](../src/main/java/com/kraft/service/post/PostImageService.java), [index.js](../src/main/resources/static/js/app/index.js).

## 6. 인증·권한의 현재 동작

| 사용자 | 읽기 | 글·댓글 등록 / 이미지 업로드 | 수정·삭제 |
| --- | --- | --- | --- |
| 비로그인 | 게시글·댓글 조회 가능 | 인증 필요 | 인증 필요 |
| 로그인한 `GUEST` | 가능 | 현재 보안 설정상 가능 | 본인 소유일 때 가능 |
| 로그인한 `USER` | 가능 | 가능 | 본인 소유일 때 가능 |
| 로그인한 `ADMIN` | 가능 | 가능 | 모든 게시글·댓글에 가능 |

가입 시 `GUEST`, 이메일 인증 시 `USER`로 변경된다. 그러나 API 보안 설정은 `authenticated()`만 요구하고, 글·댓글 등록 서비스에도 `USER` 역할 검사가 없다. **이메일 인증 전 작성 차단은 현재 구현된 정책이 아니다.** 화면에서만 차단하거나 구현된 것처럼 안내하지 않는다.

수정·삭제는 [OwnershipPolicy](../src/main/java/com/kraft/service/support/OwnershipPolicy.java)가 작성자 이메일과 현재 인증 정보를 비교하거나 관리자 역할을 확인한다. 화면 버튼 노출은 이 서버 판정에 맞춰야 한다.

화면 GET 요청은 일반적으로 공개되어 `/posts/save`, `/users/me/password`도 직접 접근할 수 있다. API의 인증 필요 조건과 화면 접근 조건을 혼동하지 않는다. 기존 [IndexControllerTest](../src/test/java/com/kraft/web/IndexControllerTest.java)도 등록 화면의 익명 접근을 검증한다.

## 7. 인증·오류 처리의 실제 연결

- 로그인 성공은 현재 항상 `/`로 이동한다. 글쓰기나 댓글 위치로 자동 복귀하는 기능은 없다.
- 로그아웃은 같은 출처의 이전 화면으로 복귀하도록 구현되어 있으며 `/posts/save`에서는 `/`로 이동한다.
- 비밀번호 변경 성공 후 브라우저가 로그아웃 폼을 제출한다. 이후 현재 정책에 따라 비밀번호 화면으로 되돌아갈 수 있다.
- 인증 토큰 유효기간은 24시간이다. 사용한 토큰은 삭제된다. 성공 시 이미 로그인한 세션의 역할을 즉시 갱신하는 처리는 확인되지 않는다.
- 메일 발송 실패는 가입 API에서 성공 응답과 구분되지 않는다. 인증 메일 재발송 API는 없다.
- REST 검증 실패·없는 게시글 등은 현재 `400`과 `ProblemDetail.detail`, 소유권 실패는 `403`을 반환한다. 인증·CSRF 실패는 보안 필터에서 처리되므로 같은 JSON 형식을 보장하지 않는다.
- 화면 컨트롤러의 없는 게시글 예외는 REST 예외 처리 대상이 아니다. 별도 HTML 오류 화면은 현재 명세상 제공되지 않는다.

근거: [EmailVerificationService](../src/main/java/com/kraft/service/user/EmailVerificationService.java), [ApiExceptionHandler](../src/main/java/com/kraft/web/exception/ApiExceptionHandler.java), [SecurityConfig](../src/main/java/com/kraft/config/security/SecurityConfig.java), [index.js](../src/main/resources/static/js/app/index.js).

## 8. 재설계에서 해결할 화면 차이

| 현재 코드에서 확인한 점 | 목표 | 필요한 변경 |
| --- | --- | --- |
| 제목 링크가 수정 폼으로 이동 | 같은 경로에서 읽기 기본 상태 제공 | 상세 템플릿 재구성 |
| 게시글 수정·삭제 버튼은 무조건 출력, 댓글 삭제는 로그인 여부만 확인 | 본인·관리자에게만 관리 버튼 출력 | 서버 화면 모델에 관리 가능 여부 추가 |
| 댓글 수정 API는 있지만 UI 없음 | 권한 있는 댓글에 인라인 수정 제공 | 권한 데이터와 JS 보완 |
| 헤더는 접히지 않는 `navbar-expand` | 작은 화면에서 메뉴로 전환 | 공통 탐색·CSS·접근성 동작 |
| 모든 페이지 번호 출력 | 제한된 페이지 창과 이전·다음 | 목록 템플릿 계산·표시 |
| 등록·계정 폼의 `col-md-6` 배치 | 작성 본문은 넓게, 계정 폼은 중앙 정렬 | 화면별 폭과 간격 정의 |
| 성공 토스트 직후 이동·새로고침 | 이동 후에도 결과 확인 가능 | 한 번만 표시하는 결과 메시지 전달 |
| 인증 완료 화면은 작성 권한이 생겼다고 안내 | 실제 역할·정책과 일치하는 인증 완료 문구 | 문구 정리. 권한 정책 변경은 별도 작업 |

이 표는 소스에서 도출한 개선 항목이며, 브라우저에서 재현·측정한 시각 감사 결과는 아니다.
