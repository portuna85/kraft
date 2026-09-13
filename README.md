# Kraft

Java 25 · Spring Boot 4.1.1 · Thymeleaf · MariaDB 기반 커뮤니티 애플리케이션입니다.
앱은 호스트에서 직접 실행하고, DB만 Docker 컨테이너로 띄웁니다.

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

`V5__unique_user_name.sql`은 `users.name`에 유니크 제약을 추가하므로, **기존 DB에 중복
닉네임이 있으면 마이그레이션이 실패합니다.** 적용 전에 확인하고 정리합니다.

```sql
SELECT name, COUNT(*) FROM users GROUP BY name HAVING COUNT(*) > 1;
```

기존 개발 DB에 `prod`를 바로 적용하면 V2의 중복 컬럼 오류가 발생할 수 있습니다.
대상 DB에 `flyway_schema_history`와 `posts.category`가 있는지 먼저 확인해 실제 상태에 맞는
`spring.flyway.baseline-version`을 정하고, 배포 전 같은 설정으로 1회 리허설합니다.

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

테스트는 `test` 프로파일의 H2 인메모리 DB를 사용하며 Docker MariaDB나 `.env`가 필요하지 않습니다.

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

복구 후에는 로그인, 이미지가 보이는 글 열기, 새 글 작성까지 실제로 해봐야 세 가지가 맞물렸는지
확인됩니다. DB만 되돌리면 `post_images` 행은 있는데 파일이 없는 상태가 될 수 있습니다.

`EMAIL_ENCRYPTION_KEY`를 바꾸려면 기존 이메일을 옛 키로 복호화해 새 키로 다시 암호화하는
절차가 필요합니다. 키만 교체하면 기존 계정의 이메일을 읽을 수 없게 되고, 로그인 조회에 쓰는
`email_hash`는 키를 쓰지 않으므로 로그인은 되는데 이메일만 깨진 상태가 됩니다.

## 문제 확인

- DB 연결 거부: `docker compose ps`와 `docker compose logs --tail 100 mariadb`로 DB 준비 상태를 확인합니다.
- DB 인증 실패: 기존 볼륨의 계정과 `.env`가 일치하는지 확인합니다. 볼륨 삭제로 해결하지 않습니다.
- 포트 충돌: 기존 프로세스를 확인하거나 `DB_PORT`를 바꾸고 `DB_URL`도 함께 수정합니다.
- 다른 기기에서 접속: `APP_BASE_URL=http://<PC 주소>:8080`을 설정합니다.
- SMTP 실패: `logs/kraft-email.log`를 확인합니다.
- 업로드 이미지가 안 지워짐: 삭제는 DB 커밋 후에 실행하고, 실패하면 `post_images`에
  `PENDING_DELETE`로 남겨 주기 작업이 다시 시도합니다(`app.upload.cleanup-*`).
  글에 연결하지 않은 업로드는 24시간 뒤 정리됩니다.
- 비밀번호 변경 후 로그아웃됨: 의도된 동작입니다. 변경 시 그 계정의 모든 기기 세션을
  서버에서 폐기하므로 다시 로그인해야 합니다.
