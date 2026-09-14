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
(지금은 V7). 그러면 마이그레이션을 하나도 실행하지 않고 "여기까지 적용됨"만 기록하며,
이어지는 `ddl-auto: validate`가 스키마와 엔티티가 맞는지 확인해 줍니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=prod --spring.flyway.baseline-version=7"
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
  응답·DB 커넥션 풀·디스크 여유·메일 대기열). 상태 확인 엔드포인트나 외부 수집기가 없어도
  "어제 이 시간과 비교해 지금이 이상한가"를 이 파일 하나로 볼 수 있습니다.

  기준을 넘기면 같은 줄이 ERROR로 올라가 `logs/kraft-error.log`에도 남으므로, 장애를 훑을 때는
  그쪽만 봐도 됩니다. 기준은 `app.metrics.*`로 조정합니다 — 너무 예민하면 아무도 로그를 보지
  않게 되므로, 실제로 울린 것을 보고 맞춰 가는 편이 좋습니다.

  ```powershell
  Get-Content logs\kraft-metrics.log -Tail 20 -Wait
  ```
- 비밀번호 변경 후 로그아웃됨: 의도된 동작입니다. 변경 시 그 계정의 모든 기기 세션을
  서버에서 폐기하므로 다시 로그인해야 합니다.
