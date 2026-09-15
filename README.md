# Kraft

Java 25 · Spring Boot 4.1.1 · Thymeleaf · MariaDB 기반 커뮤니티 애플리케이션입니다.
앱은 호스트에서 직접 실행하고, DB만 Docker 컨테이너로 띄웁니다.

## 프로젝트 구조

Java 코드는 기능을 먼저 구분하고, 각 기능 안에서 책임별로 나눕니다. 테스트도 같은 패키지를
따릅니다. 새로운 게시글 기능은 `post` 아래에서 도메인·서비스·DTO·웹 진입점을 함께 찾을 수 있습니다.

| 패키지 (`com.kraft` 기준) | 책임 |
| --- | --- |
| `post/{domain,service,dto,web}` | 게시글·검색·추천·이미지와 게시판 화면 |
| `comment/{domain,service,dto,web}` | 댓글과 댓글 API |
| `report/{domain,service,dto,web}` | 신고 접수와 관리자 처리 화면 |
| `user/{domain,service,dto,web}` | 회원가입·비밀번호·인증과 회원 화면 |
| `user/mail` | 발송 인터페이스·SMTP 구현·메일 대기열·발송 작업자 |
| `shared/domain` | 공통 감사 필드와 본문 길이 정책 |
| `shared/security` | 작성 권한·소유권 정책 |
| `shared/transaction` | DB 커밋 후 실행 지원 |
| `shared/web` | API 예외 응답·탐색 모델·페이지 계산·안전한 복귀 경로 |
| `config`, `config/security` | JPA·웹·인증 구성 |
| `observability` | 요청 지표와 상태 점검 |
| `operations/rekey` | 이메일 암호화 키 교체 도구 |

의존 방향은 `web → service → domain`이며, 서비스와 웹이 공유하는 DTO는 기능별 `dto`에 둡니다.
도메인은 서비스나 웹에 의존하지 않습니다. 게시글을 찾지 못한 예외는 `post/domain`에 두고,
웹 계층에서 HTML 또는 JSON 404 응답으로 바꿉니다. `shared/web`의 어드바이스는 기능별 웹
진입점을 연결하는 역할이며, 비즈니스 서비스에서는 참조하지 않습니다.

`config/security/KraftUserDetails`는 JDBC 세션에 직렬화된 클래스 이름과의 호환성을 위해 기존
패키지를 유지합니다. 클래스 이동만으로도 기존 로그인 세션을 읽지 못할 수 있으므로 주의합니다.

| 소스 경로 | 용도 |
| --- | --- |
| `src/main/java`, `src/main/resources` | 운영 애플리케이션과 템플릿·정적 자산 |
| `src/test/java`, `src/test/resources` | JUnit 단위·통합·MariaDB 테스트 |
| `src/e2e/java`, `src/e2e/resources` | 브라우저 테스트 서버의 시드·메일 기록기·설정 |
| `e2e` | Playwright 시나리오와 시각 회귀 기준 이미지 |
| `src/vue`, `src/styles` | Vue·SCSS 원본 |
| `types` | 브라우저 전역 타입 선언 |

정적 JS는 `src/main/resources/static/js/app`에서 직접 제공하고, Vue·SCSS는 빌드한 파일도
커밋합니다. 따라서 운영 Gradle 빌드는 Node.js 없이 실행할 수 있습니다. CSS·Vue 산출물,
Gradle Wrapper, 의존성 잠금 파일, 시각 회귀 기준 이미지는 재현 가능한 빌드·테스트에 필요합니다.

## 실행

Java 25와 Docker Desktop(Linux 컨테이너)이 필요합니다. 프로젝트 루트에서 진행합니다.
`.env`가 없을 때만 예제 파일을 복사하고 실제 값을 입력합니다.

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
docker compose up -d --wait
.\gradlew.bat bootRun
```

`mariadb`가 `healthy`가 된 뒤 앱을 실행하고 [http://localhost:8080](http://localhost:8080)에 접속합니다.
`local`이 기본 프로파일이며 프로젝트 루트의 `.env`를 자동으로 읽습니다. 셸에서 값을 따로 로드할 필요가 없습니다.
bash에서는 `./gradlew bootRun`을 사용합니다.
IntelliJ IDEA에서는 `com.kraft.KraftApplication`의 `Working directory`를 `$PROJECT_DIR$`로 설정합니다.
실행 구성에 DB 환경변수가 남아 있으면 그 값이 `.env`보다 우선하므로 함께 확인합니다.

## 환경변수와 데이터 보존

| 설정 | 역할 |
| --- | --- |
| `MARIADB_DATABASE`, `MARIADB_USER`, `MARIADB_PASSWORD` | DB 컨테이너 초기화 |
| `MARIADB_ROOT_PASSWORD` | DB 초기화용 관리자 비밀번호, 앱에는 전달하지 않음 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 앱의 DB 접속 정보, `MARIADB_*`와 맞춤 |
| `DB_PORT` | DB의 호스트 공개 포트, 기본 `127.0.0.1:3306` |
| `EMAIL_ENCRYPTION_KEY` | 저장된 이메일 암호화 키. 기존 데이터가 있으면 같은 키 유지 |
| `APP_BASE_URL` | 이메일 인증 링크의 브라우저 주소, 기본 `http://localhost:8080` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | 실제 SMTP 설정 |

`.env`는 Java properties와 호환되도록 따옴표나 `export` 없이 `KEY=value`로 작성합니다.
같은 이름의 OS 환경변수가 우선합니다. 필수 DB 값이 비어 있으면 Compose가 실행 전에 오류를 표시합니다.
기존 DB 볼륨에서는 `MARIADB_*`를 바꿔도 저장된 계정·비밀번호가 자동 변경되지 않습니다.
`prod`와 `test` 프로파일은 `.env`를 읽지 않습니다.

| 데이터 | 저장 위치 |
| --- | --- |
| DB·로그인 세션 | Docker 볼륨 `kraft_kraft-mariadb-data` |
| 업로드 이미지 | `uploads/images/` (소유권·상태는 DB의 `post_images`) |
| 앱 로그 | `logs/` |
| 자동 테스트 로그 | `build/test-logs/` |

`local`은 Hibernate `update`와 멱등 세션 SQL을 사용해 재기동 후에도 기존 데이터를 유지합니다.
운영 배포는 `prod`의 Flyway + `validate` 경로를 별도로 사용합니다.
로컬 세션 초기화도 `db/migration/V3__spring_session.sql`을 직접 재사용합니다. V3 주석에 남은
과거의 별도 세션 SQL 경로는 더 이상 사용하지 않습니다. 적용된 마이그레이션의 체크섬을 유지하기
위해 과거 SQL 파일과 주석은 수정하지 않습니다.

빈 DB에서의 마이그레이션 적용은 `MariaDbMigrationTest`가, **이미 데이터가 있는 기존 DB의
전환**은 `MariaDbUpgradeRehearsalTest`가 각각 실제 MariaDB로 검증합니다. 후자는 `local`이
`ddl-auto: update`로 만든 스키마에 운영 설정을 적용하는 경로를 그대로 리허설합니다.

`V5__unique_user_name.sql`은 `users.name`에 유니크 제약을 추가하므로, **기존 DB에 중복
닉네임이 있으면 마이그레이션이 실패합니다.** 적용 전에 확인하고 정리합니다.

```sql
SELECT name, COUNT(*) FROM users GROUP BY name HAVING COUNT(*) > 1;
```

**기존 개발 DB에 `prod`를 바로 적용하면 실패합니다.** `application-prod.yml`의
`baseline-version: "1"`은 Flyway를 처음 도입하는 빈 DB 기준이라, 이미 `posts.category`가
있는 DB에서는 V2가 `Duplicate column name 'category'`로 멈춥니다.

이미 현재 엔티티로 만들어진 DB라면 **디스크의 최신 마이그레이션 버전으로 baseline** 합니다
(지금은 V11). 그러면 마이그레이션을 하나도 실행하지 않고 "여기까지 적용됨"만 기록하며,
이어지는 `ddl-auto: validate`가 스키마와 엔티티가 맞는지 확인해 줍니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=prod --spring.flyway.baseline-version=11"
```

이 네 단계(기존 DB 재현 → 잘못된 baseline이 실패 → 올바른 baseline → validate 통과)는
`MariaDbUpgradeRehearsalTest`가 실제 MariaDB에서 그대로 검증하므로, 손으로 리허설할 필요가
없습니다. 다만 **운영 DB가 현재 엔티티보다 오래된 스키마라면** baseline 값이 달라지므로,
`flyway_schema_history`와 실제 컬럼을 먼저 확인합니다.

## 입력 길이 정책

저장소 한계와 어긋나지 않도록 입력 검증·컬럼·세션을 같은 기준으로 맞춰 둡니다.

| 항목 | 최대 | 결정 근거 |
| --- | ---: | --- |
| 이메일 | 100자 | 가장 좁은 경계인 `SPRING_SESSION.PRINCIPAL_NAME VARCHAR(100)`에 맞춤 (`EmailPolicy`) |
| 이름(닉네임) | 50자 | `users.name VARCHAR(50)` |
| 게시글 제목 | 255자 | `posts.title VARCHAR(255)` |
| 게시글 본문 | 10,000자 | `TEXT`는 65,535바이트. 한 자 최대 3바이트 기준 30,000바이트 (`ContentPolicy`) |
| 댓글 본문 | 1,000자 | 위와 같은 기준으로 3,000바이트 |

이메일은 AES 암호화 후 hex로 저장되어 `평문 × 2 + 64`자가 됩니다(100자 → 264자,
`users.email VARCHAR(500)`). 로그인 식별자가 이메일 전체이므로 세션의 `PRINCIPAL_NAME`
100자가 실질 상한이고, 그보다 긴 주소는 가입 시점에 거부합니다 — 예전에는 101~218자 주소로
가입은 되는데 로그인이 안 되는 계정이 만들어졌습니다.

## API 계약

화면(Vue 아일랜드)이 쓰는 REST API입니다. 경로는 모두 `/api/v1` 아래에 있고, 응답은
JSON입니다. 이 표는 `SecurityConfig`·컨트롤러·`ApiExceptionHandler`의 현재 동작을 옮긴
것이며, 별도의 공개 API 클라이언트를 상정한 규격은 아닙니다.

### 인증과 CSRF

로그인은 폼 로그인(`POST /login`)이고, 이후에는 `SESSION` 쿠키로 인증합니다(세션은 DB에
저장되어 앱을 재시작해도 유지됩니다). 토큰 발급 엔드포인트는 없습니다.

GET이 아닌 요청에는 CSRF 토큰이 필요합니다. 화면은 `layout/header`가 심은
`<meta name="_csrf">`·`<meta name="_csrf_header">`를 읽어 헤더로 실어 보냅니다
(`static/js/app/core/http.js`). 토큰 없이 보내면 403입니다.

**세션이 끊긴 뒤의 변경 요청은 401도 리다이렉트도 아닌 403입니다.** CSRF 토큰이 세션에
저장되므로, 세션이 사라지면 대조할 곳이 없어 `CsrfFilter`가 인증 진입점보다 먼저 거절합니다.
화면은 이 403을 "다시 로그인해 주세요"로 안내합니다(`e2e/session-expired.spec.js`).

권한은 세 단계입니다.

| 단계 | 의미 | 해당 |
| --- | --- | --- |
| 누구나 | 로그인 불필요 | 게시글·댓글 **조회**, 회원가입 |
| 로그인 | 세션 필요 | 추천, 비밀번호 변경, 인증 메일 재발송, 회원 탈퇴 |
| 이메일 인증 완료 | `GUEST`와 **정지 중인 계정**은 거부(`WriteAccessPolicy`) | 글·댓글 작성·수정, 이미지 업로드 |
| 관리자 | `ROLE_ADMIN`만 통과 | 신고 처리 화면(`/admin/**`)과 처리 API(`/api/v1/admin/**`) |

수정·삭제는 여기에 더해 **작성자 본인 또는 관리자**여야 합니다(`OwnershipPolicy`).
공지(`NOTICE`) 분류는 관리자만 쓸 수 있습니다(`CategoryPolicy`).

### 엔드포인트

| 메서드 | 경로 | 권한 | 요청 | 성공 응답 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/posts` | 누구나 | `page`, `size`, `q`, `category` | 200 · 페이지(`content`, `page`, `totalElements`, `totalPages`, `first`, `last`) |
| GET | `/api/v1/posts/{id}` | 누구나 | — | 200 · 게시글(`id`, `title`, `content`, `picture`, `author`, `category`, `viewCount`) |
| POST | `/api/v1/posts` | 인증 완료 | `title`, `content`, `picture`, `category` | 200 · 생성된 id |
| PUT | `/api/v1/posts/{id}` | 본인·관리자 | 위 + `version` | 200 · id |
| DELETE | `/api/v1/posts/{id}` | 본인·관리자 | — | 200 · id |
| PUT | `/api/v1/posts/{id}/like` | 로그인 | `liked` | 200 · `liked`, `likeCount` |
| POST | `/api/v1/posts/images` | 인증 완료 | `multipart/form-data`의 `file` | 200 · `url` |
| GET | `/api/v1/posts/{postId}/comments` | 누구나 | — | 200 · 댓글 목록 |
| POST | `/api/v1/posts/{postId}/comments` | 인증 완료 | `content` | 200 · 생성된 id |
| PUT | `/api/v1/comments/{id}` | 본인·관리자 | `content` | 200 · id |
| DELETE | `/api/v1/comments/{id}` | 본인·관리자 | — | 200 · id |
| POST | `/api/v1/reports` | 로그인 | `targetType`, `targetId`, `reason`, `detail` | 200 · 생성된 id |
| POST | `/api/v1/admin/reports/{id}/resolve` | 관리자 | `suspendDays`(선택) | 204 (대상 삭제, 0보다 크면 작성자 정지) |
| POST | `/api/v1/admin/reports/{id}/reject` | 관리자 | — | 204 (대상 유지) |
| POST | `/api/v1/users` | 누구나 | `name`, `email`, `password` | 200 · 생성된 id |
| DELETE | `/api/v1/users/me` | 로그인 | `currentPassword` | 204 (탈퇴. 모든 세션이 폐기됩니다) |
| POST | `/api/v1/users/password-reset` | 누구나 | `email` | 204 (가입 여부와 무관하게 항상 같다) |
| POST | `/api/v1/users/password-reset/confirm` | 누구나(토큰 필요) | `token`, `newPassword` | 204 (이 계정의 모든 세션이 폐기됩니다) |
| PUT | `/api/v1/users/me/password` | 로그인 | `currentPassword`, `newPassword` | 204 (이 계정의 모든 세션이 폐기됩니다) |
| POST | `/api/v1/users/me/verify-email/resend` | 로그인 | — | 204 |

`PUT /api/v1/posts/{id}`의 `version`은 편집을 시작할 때 화면이 받아간 값을 그대로 돌려보내는
것입니다. 그 사이 다른 곳에서 저장됐으면 409로 거절합니다 — 예전에는 나중 저장이 먼저 저장을
말없이 덮어썼습니다. 입력 길이 제한은 위 "입력 길이 정책"을 따릅니다.

`liked`는 토글이 아니라 **원하는 최종 상태**입니다. 같은 값을 여러 번 보내도 결과가 같습니다.

### 신고와 처리

글·댓글의 "신고" 버튼에서 사유를 골라 접수하고, 관리자가 `/admin/reports`에서 처리합니다.
자기 글은 신고할 수 없고(직접 지우면 됩니다), 같은 대상을 두 번 신고할 수도 없습니다
(`UK_REPORT_REPORTER_TARGET`).

관리자의 선택은 셋입니다. **삭제**는 대상 글·댓글을 지우고 신고를 닫고, **삭제 + 7일 정지**는
거기에 더해 작성자의 작성 권한을 그 기간 동안 막으며, **반려**는 대상을 그대로 둔 채 신고만
닫습니다.

정지는 **작성만** 막습니다. 읽기와 로그인은 그대로 두는데, 정지된 사람도 자기 상태와 사유를
볼 수 있어야 하고 그러려면 들어올 수는 있어야 하기 때문입니다. 글쓰기 화면과 댓글 입력창은
폼 대신 "언제까지, 왜"를 보여줍니다 — 작성 경로와 같은 `WriteAccessPolicy`가 그 문장을
만들므로 화면과 서버의 판단이 갈라지지 않습니다.

기간은 `users.suspended_until`에 시각으로 저장하고 매번 현재 시각과 비교합니다. 해제 배치가
없으므로 **배치가 멈춰서 정지가 안 풀리는 일도 없습니다.** 처리한 신고도 행으로 남습니다 — 같은 대상이 반복해서
신고되는지, 관리자가 무엇을 언제 지웠는지가 남아야 나중에 설명할 수 있습니다.

삭제는 기존 삭제 경로(`PostService`/`CommentService`)를 그대로 부릅니다. 그쪽이 이미 소유권
검사·이미지 정리 예약·커밋 후 파일 정리를 맡고 있어, 저장소를 직접 지우면 그 뒷정리가 빠집니다.
한 대상에 신고가 여러 건 쌓여 있으면 하나를 처리할 때 나머지도 함께 정리합니다.

신고 대상은 FK가 아니라 `target_type + target_id`로 가리킵니다(대상이 앞으로 늘 수 있습니다).
그래서 **대상이 이미 사라진 신고**가 있을 수 있고, 화면은 그것을 정상으로 다룹니다.

### 회원 탈퇴

계정 메뉴의 "회원 탈퇴"에서 현재 비밀번호를 확인한 뒤 진행합니다. 되돌릴 수 없습니다.

**행을 지우지 않고 개인정보만 지웁니다.** `posts.user_id`·`comments.user_id`가 NOT NULL이라
회원을 지우려면 그 사람의 글과 댓글을 모두 지워야 하는데, 그러면 남의 댓글이 달린 글이나
대화의 맥락까지 함께 사라집니다. 글은 남기고 작성자만 `탈퇴한 사용자{id}`로 바꿉니다.

탈퇴 시점에 이름·이메일·비밀번호는 쓸 수 없는 값으로 덮어쓰고(`users.withdrawn_at` 기록),
남아 있던 인증·재설정 링크와 보낼 예정이던 메일도 지웁니다. 이메일이 바뀌므로 `email_hash`도
함께 바뀌어 **같은 주소로 다시 가입할 수 있습니다** — 탈퇴가 그 주소를 영영 잠그면 안 됩니다.
탈퇴한 계정은 로그인 조회 단계에서 없는 계정으로 취급합니다(`UserDetailsServiceImpl`).

### 비밀번호 찾기

로그인해야 비밀번호를 바꿀 수 있는데 잊은 사람은 로그인할 수 없으므로, 메일로 보낸 1회용
링크가 그 고리를 끊습니다. 로그인 화면의 "비밀번호를 잊으셨나요?"에서 시작합니다.

1. `/forgot-password`에서 주소를 넣으면 `POST /api/v1/users/password-reset`이 나갑니다.
2. 서버는 가입된 주소일 때만 30분짜리 토큰을 만들어 메일 대기열에 넣습니다. 옛 링크는 이때
   무효가 되므로 메일함에 살아 있는 링크는 항상 하나입니다.
3. 메일의 링크(`/users/password-reset?token=...`)에서 새 비밀번호를 정하면
   `POST /api/v1/users/password-reset/confirm`이 나갑니다. 토큰은 쓰는 즉시 지워지고,
   그 계정의 **모든 기기 세션이 폐기**됩니다.

**요청 단계는 어떤 경우에도 똑같이 204입니다.** 가입하지 않은 주소든, 1분 안에 다시 요청해
제한에 걸렸든 결과가 같습니다 — 응답이 갈리면 그것만으로 가입 여부를 확인하는 도구가 됩니다.
화면도 늘 "가입된 주소라면 재설정 링크를 보냈습니다"라고만 안내합니다. 반대로 링크를 이미 받은
사람에게는 확인 단계에서 만료·무효 사유를 분명히 알려줍니다.

요청 제한(60초)은 **메일 종류별로** 겁니다. 가입 직후 인증 메일을 받은 사람이 곧바로 비밀번호를
잊어도 재설정 요청이 막히지 않아야 하기 때문입니다.

### 오류 형식

오류는 RFC 9457 `application/problem+json`으로 나가며 `status`와 `detail`을 담습니다.
`detail`은 사용자에게 그대로 보여줄 수 있는 한국어 문장이고, 500만은 내부 사정을 감춘
고정 문구입니다(원인은 서버 로그에 남습니다).

| 상태 | 언제 |
| ---: | --- |
| 400 | 입력 검증 실패, 읽을 수 없는 JSON, 경로 변수 형식 오류, 서비스의 검증 실패(중복 가입 등) |
| 403 | 본인·관리자가 아님, 이메일 인증 전 작성·업로드 시도, CSRF 토큰이 없거나 맞지 않음 |
| 404 | 없는 게시글 |
| 409 | 편집 충돌(`version` 불일치), 유니크 제약 위반(같은 이름 동시 가입 등) |
| 413 | 업로드가 수신 한도를 넘음 |
| 500 | 그 밖의 처리되지 못한 예외 |

## 중지·재시작

```powershell
docker compose stop
docker compose up -d --wait
```

`docker compose down`은 컨테이너·네트워크를 제거하고 데이터 볼륨은 유지합니다.
`down -v`는 DB 데이터도 삭제하므로 일반 재시작이나 연결 오류 해결에 사용하지 않습니다.
`update`는 컬럼·테이블 추가만 반영하므로, 엔티티에서 필드를 지우거나 타입을 바꿨다면
백업 후 명시적인 SQL 또는 검증한 Flyway 마이그레이션으로 진행합니다.

앱이 스키마를 전혀 건드리지 않게 하고 연결만 확인하려면 다음 명령을 사용합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.jpa.hibernate.ddl-auto=validate --spring.session.jdbc.initialize-schema=never"
```

## 테스트

```powershell
.\gradlew.bat test
```

`test`가 끝나면 JaCoCo 커버리지 리포트가 함께 만들어집니다
(`build/reports/jacoco/test/html/index.html`). 실패시킬 문턱값은 두지 않았습니다 — 이 저장소의
안전망은 수치가 아니라 실제 DB·브라우저까지 밟는 검증이고, JaCoCo는 별도 프로세스로 도는
브라우저 테스트의 실행을 세지 못합니다. 리포트는 "어디가 비어 있는지"를 보는 용도입니다.
DTO(`com.kraft.**.dto`)는 동작이 없는 생성 코드라 분모에서 제외합니다.

운영 실행 JAR은 `bootJar`, 브라우저 테스트용 실행 JAR은 `bootE2eJar`로 만듭니다.
운영 JAR에는 E2E 시드·메일 조회 API·H2가 포함되지 않습니다.

```powershell
.\gradlew.bat bootJar bootE2eJar
npm ci
npm run lint
npm run check:css
npm run check:vue
npx playwright install chromium
npx playwright test --project=chromium
```

Vue·SCSS 원본을 수정했다면 먼저 `npm run build:vue` / `npm run build:css`로 생성 파일을
갱신하고 함께 커밋합니다. `check:*`는 재빌드한 생성 파일과 Git에 기록된 파일의 차이를 검사하므로
의도한 변경도 커밋 전에는 차이로 표시됩니다. 자산을 변경한 뒤 브라우저 테스트를 실행할 때는
`bootE2eJar`도 다시 빌드합니다. Playwright는 `build/libs/kraft-0.0.1-SNAPSHOT-e2e.jar`를
매 실행마다 새로 띄우며, 8081 포트가 이미 사용 중이면 실패합니다.

대부분의 테스트는 `test` 프로파일의 H2 인메모리 DB를 사용하며 Docker MariaDB나 `.env`가
필요하지 않습니다.

예외는 아래 세 클래스입니다. Testcontainers로 실제 MariaDB를 띄웁니다.

- `MariaDbMigrationTest` — **빈 DB**에서 `db/migration`의 모든 마이그레이션을 순서대로 실행하고,
  `ddl-auto: validate`로 "마이그레이션이 만든 스키마와 엔티티 매핑이 일치하는지"를 확인합니다.
- `MariaDbUpgradeRehearsalTest` — **이미 데이터가 있는 기존 DB**를 운영 설정으로 넘기는 절차를
  리허설합니다. 위 "환경변수와 데이터 보존"의 baseline 안내가 여기서 검증됩니다.
- `BackupRestoreRehearsalTest` — 아래 "백업·복구" 절차를 그대로 밟습니다. 덤프를 뜨고, DB와
  업로드 파일을 지우고, 되돌린 뒤, 앱이 정상 기동해 글·이미지·이메일이 살아 있는지 봅니다.

앞의 두 클래스가 따로 필요한 이유는 H2 때문입니다. H2는 Hibernate가 엔티티로 스키마를 직접
만들기 때문에 **마이그레이션 SQL을 한 줄도 실행하지 않습니다.**

Docker가 없으면 이 클래스들만 건너뛰므로 `gradlew test`는 그대로 통과합니다. 다만 그때는
마이그레이션도 복구 절차도 검증되지 않은 것이므로, **운영 배포 전에는 Docker를 켠 상태로 한 번
돌려야 합니다.** CI는 Docker가 있는 환경에서 항상 실행합니다.

## 백업·복구

DB만 받아두면 복구되지 않습니다. **세 가지를 같은 시점으로 함께** 보관해야 합니다.

| 대상 | 이유 |
| --- | --- |
| DB 덤프 | 회원·게시글·댓글·세션 |
| `uploads/images/` | 이미지 파일. DB의 `post_images`가 이 파일들을 가리킵니다 |
| `EMAIL_ENCRYPTION_KEY` | 이 키가 없으면 복구한 DB의 이메일을 복호화할 수 없습니다 |

```powershell
docker compose exec mariadb mariadb-dump -u root -p"$env:MARIADB_ROOT_PASSWORD" --single-transaction kraft > backup.sql
Compress-Archive -Path uploads -DestinationPath uploads-backup.zip
```

### 복구

앱을 내린 상태에서 **세 가지를 같은 시점의 것으로** 되돌립니다. 순서는 상관없지만 하나라도
빠지면 아래 표처럼 어긋납니다.

```powershell
# 1. DB
Get-Content backup.sql | docker compose exec -T mariadb mariadb -u root -p"$env:MARIADB_ROOT_PASSWORD" kraft

# 2. 업로드 파일
Expand-Archive -Path uploads-backup.zip -DestinationPath . -Force

# 3. .env의 EMAIL_ENCRYPTION_KEY를 백업 시점의 값으로 되돌린 뒤 기동
.\gradlew.bat bootRun
```

| 빠뜨린 것 | 겉보기 | 실제 상태 |
| --- | --- | --- |
| 업로드 파일 | **앱은 오류를 내지 않는다** | `post_images` 행은 있는데 파일이 없어 이미지만 깨져 보인다 |
| 암호화 키 | DB 복구는 성공한 것으로 보인다 | 그 회원을 **읽어 올 수조차 없다** — 복호화가 엔티티 생성 시점에 일어난다 |

복구 후에는 로그인, 이미지가 보이는 글 열기, 새 글 작성까지 실제로 해봐야 세 가지가 맞물렸는지
확인됩니다. 이 절차와 위 두 가지 실패 양상은 `BackupRestoreRehearsalTest`가 실제 MariaDB로
검증하므로, 절차 자체가 낡아 못 쓰게 되는 일은 없습니다.

### 이메일 암호화 키 교체

`EMAIL_ENCRYPTION_KEY`를 **키만 갈아 끼우면 안 됩니다.** 기존 계정의 이메일을 읽을 수 없게
되는데, 로그인 조회에 쓰는 `email_hash`는 키를 쓰지 않으므로 **로그인은 계속 되고 이메일만
깨진** 상태가 되어 한참 뒤에야 발견됩니다.

저장된 이메일을 옛 키로 복호화해 새 키로 다시 암호화하는 전용 실행이 있습니다. 앱을 내린
상태에서 한 번 돌리고 종료합니다.

```powershell
# 1. 앱을 내리고 DB를 먼저 백업합니다(위 명령). 이 단계를 건너뛰지 않습니다.
$env:EMAIL_ENCRYPTION_KEY = "<새 키>"
$env:EMAIL_ENCRYPTION_KEY_OLD = "<지금 쓰는 키>"
java -jar build\libs\kraft-0.0.1-SNAPSHOT.jar --spring.profiles.active=rekey

# 2. 끝나면 .env의 EMAIL_ENCRYPTION_KEY를 새 키로 바꾸고 평소대로 기동합니다.
# 3. 로그인과 인증 메일 재발송까지 실제로 해봅니다.
```

종료 코드 0이면 성공입니다. `rekey` 프로파일은 스키마도 건드리지 않고 포트도 열지 않으며,
메일·정리·관측 주기 작업을 모두 끕니다.

행마다 **평문의 SHA-512가 그 행의 `email_hash`와 일치하는지 확인한 뒤에** 씁니다. 옛 키가
틀렸거나 값이 어긋나면 그 자리에서 멈추고 `userId`를 알립니다 — 나머지를 조용히 덮어쓰지
않습니다. 중간에 중단되어도 **다시 돌리면 남은 것만** 처리하므로 재실행이 안전합니다.
이메일 주소는 로그에 남기지 않습니다.

검증은 `EmailRekeyServiceTest`가 실제 DB 행으로 합니다.

## 문제 확인

- DB 연결 거부: `docker compose ps`와 `docker compose logs --tail 100 mariadb`로 DB 준비 상태를 확인합니다.
- DB 인증 실패: 기존 볼륨의 계정과 `.env`가 일치하는지 확인합니다. 볼륨 삭제로 해결하지 않습니다.
- 포트 충돌: 기존 프로세스를 확인하거나 `DB_PORT`를 바꾸고 `DB_URL`도 함께 수정합니다.
- 다른 기기에서 접속: `APP_BASE_URL=http://<PC 주소>:8080`을 설정합니다.
- SMTP 실패: `logs/kraft-email.log`와 **`outbox_mails` 테이블**을 확인합니다. 메일은 요청
  트랜잭션 안에서 보내지 않고 이 대기열을 거쳐 나가므로, 상태와 실패 원인이 행에 남습니다.

  ```sql
  SELECT id, user_id, status, attempts, last_error, created_at, sent_at
  FROM outbox_mails WHERE status <> 'SENT' ORDER BY id DESC;
  ```

  `PENDING`은 아직 보내지 않은 것(주기 작업이 다시 시도합니다), `FAILED`는 재시도 횟수를
  모두 쓴 것입니다. 원인을 고친 뒤 다시 보내려면 해당 행을 `PENDING`으로 되돌리고
  `attempts`를 0으로 낮춥니다. 발송 주기와 재시도 횟수는 `app.mail.*`로 조정합니다.
- 업로드 이미지가 안 지워짐: 삭제는 DB 커밋 후에 실행하고, 실패하면 `post_images`에
  `PENDING_DELETE`로 남겨 주기 작업이 다시 시도합니다(`app.upload.cleanup-*`).
  글에 연결하지 않은 업로드는 24시간 뒤 정리됩니다.
- 평소 상태 확인: `logs/kraft-metrics.log`에 5분마다 한 줄씩 남습니다(요청 수·오류율·평균
  응답·DB 커넥션 풀·디스크 여유·메일 대기열·미처리 신고). 상태 확인 엔드포인트나 외부 수집기가
  없어도 "어제 이 시간과 비교해 지금이 이상한가"를 이 파일 하나로 볼 수 있습니다.

  미처리 신고만 성격이 다릅니다. 앱은 멀쩡한데 **사람이 신고를 보고 있지 않다**는 신호이며,
  쌓이는 동안 신고된 글은 그대로 보입니다. 이 값이 ERROR로 올라오면 `/admin/reports`를 엽니다.

  기준을 넘기면 같은 줄이 ERROR로 올라가 `logs/kraft-error.log`에도 남으므로, 장애를 훑을 때는
  그쪽만 봐도 됩니다. 기준은 `app.metrics.*`로 조정합니다 — 너무 예민하면 아무도 로그를 보지
  않게 되므로, 실제로 울린 것을 보고 맞춰 가는 편이 좋습니다.

  ```powershell
  Get-Content logs\kraft-metrics.log -Tail 20 -Wait
  ```
- 비밀번호 변경 후 로그아웃됨: 의도된 동작입니다. 변경 시 그 계정의 모든 기기 세션을
  서버에서 폐기하므로 다시 로그인해야 합니다.
