# Kraft

Java 25 · Spring Boot 4.1.1 · Thymeleaf · MariaDB 기반 커뮤니티 애플리케이션입니다.

## Docker로 전체 실행

Docker Desktop의 Linux 컨테이너 엔진을 실행하고 프로젝트 루트에서 진행합니다.
호스트에 Java나 Gradle을 설치하지 않아도 Docker 이미지 안에서 테스트와 빌드를 수행합니다.

`.env`가 없을 때만 예제 파일을 복사하고 실제 값을 입력합니다.

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
```

`app`과 `mariadb`가 모두 `healthy`가 되면 [http://localhost:8080](http://localhost:8080)에 접속합니다.
처음 실행할 때는 Java 이미지와 Gradle 의존성을 다운로드하므로 시간이 걸릴 수 있습니다.
DB가 준비된 뒤 앱이 시작되며, 앱 상태 검사는 HTTP 응답과 실제 DB 연결을 확인합니다.

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
docker compose logs --tail 100 app mariadb
```

상태 확인 URL은 로그인 없이 `{"status":"UP"}`을 반환합니다. DB 연결이 끊어지면 readiness는 503을 반환합니다.
`/actuator/health/liveness`는 앱의 생존 상태를 확인합니다. readiness는 선택 기능인 외부 SMTP에 접속하지 않습니다.

## 환경변수와 데이터 보존

| 설정 | 역할 |
| --- | --- |
| `MARIADB_DATABASE`, `MARIADB_USER`, `MARIADB_PASSWORD` | DB 초기화와 Docker 앱 접속에 공통으로 사용 |
| `MARIADB_ROOT_PASSWORD` | DB 초기화용 관리자 비밀번호, 앱에는 전달하지 않음 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 호스트의 IDE/`bootRun` 접속용, Docker 앱은 `mariadb:3306`으로 접속 |
| `EMAIL_ENCRYPTION_KEY` | 저장된 이메일 암호화 키. 기존 데이터가 있으면 같은 키 유지 |
| `APP_PORT`, `APP_BIND_ADDRESS` | 앱의 호스트 공개 포트·주소, 기본 `127.0.0.1:8080` |
| `DB_PORT` | DB의 호스트 공개 포트, 기본 `127.0.0.1:3306` |
| `APP_BASE_URL` | 이메일 인증 링크의 브라우저 주소, 기본 `http://localhost:8080` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | 실제 SMTP 설정 |

`.env`는 Java properties와 호환되도록 따옴표나 `export` 없이 `KEY=value`로 작성합니다.
같은 이름의 OS 환경변수가 우선합니다. 필수 DB 값이 비어 있으면 Compose가 실행 전에 오류를 표시합니다.
기존 DB 볼륨에서는 `MARIADB_*`를 바꿔도 저장된 계정·비밀번호가 자동 변경되지 않습니다.

Docker 앱은 `docker` 프로파일, IDE 실행은 `local` 프로파일을 사용합니다.
두 프로파일은 Hibernate `update`와 멱등 세션 SQL을 사용하고 기존 데이터를 유지합니다.
운영 배포는 `prod`의 Flyway + `validate` 경로를 별도로 사용합니다.
기존 개발 DB에 `prod`를 바로 적용하면 V2의 중복 컬럼 오류가 발생할 수 있습니다.
대상 DB에 `flyway_schema_history`와 `posts.category`가 있는지 먼저 확인해 실제 상태에 맞는
`spring.flyway.baseline-version`을 정하고, 배포 전 같은 설정으로 1회 리허설합니다.

| 데이터 | 저장 위치 |
| --- | --- |
| DB·로그인 세션 | 기존 Docker 볼륨 `kraft_kraft-mariadb-data` |
| 업로드 이미지 | 호스트 `uploads/images/` ↔ 컨테이너 `/app/uploads/images/` |
| Docker 앱 로그 | 호스트 `logs/docker/` ↔ 컨테이너 `/app/logs/` |
| IDE 앱 로그 | `logs/` |
| 자동 테스트 로그 | `build/test-logs/` |

컨테이너 앱은 UID/GID `10001`로 실행합니다. Linux 호스트에서는 `uploads/`와 `logs/docker/`를 미리 만들고
해당 사용자에게 쓰기 권한을 부여해야 합니다. Docker Desktop에서는 공유 폴더 권한을 사용합니다.

## 중지·재시작·업데이트

```powershell
docker compose stop
docker compose up -d --wait
```

코드를 변경한 뒤에는 다시 빌드합니다.

```powershell
docker compose up -d --build --wait
```

`docker compose down`은 컨테이너·네트워크를 제거하고 데이터 볼륨은 유지합니다.
`down -v`는 DB 데이터도 삭제하므로 일반 재시작이나 연결 오류 해결에 사용하지 않습니다.
스키마 변경은 백업 후 명시적인 SQL 또는 검증한 Flyway 마이그레이션으로 진행합니다.

## IDE / 호스트에서 실행

Java 25가 필요합니다. Docker 앱과 IDE 앱이 같은 8080 포트를 사용하지 않도록 먼저 Docker 앱을 중지합니다.

```powershell
docker compose stop app
docker compose up -d --wait mariadb
.\gradlew.bat bootRun
```

`local`이 기본 프로파일이며 프로젝트 루트의 `.env`를 자동으로 읽습니다.
`DB_URL`은 `jdbc:mariadb://localhost:<DB_PORT>/<MARIADB_DATABASE>`로 설정하고,
`DB_USERNAME`·`DB_PASSWORD`는 `MARIADB_USER`·`MARIADB_PASSWORD`와 맞춥니다.
IntelliJ IDEA에서는 `com.kraft.KraftApplication`의 `Working directory`를 `$PROJECT_DIR$`로 설정합니다.
bash에서는 `./gradlew bootRun`을 사용합니다.

## 테스트

```powershell
.\gradlew.bat test
```

테스트는 `test` 프로파일의 H2 인메모리 DB를 사용하며 Docker MariaDB나 `.env`가 필요하지 않습니다.
Docker 이미지 빌드도 같은 테스트를 통과해야 실행 JAR를 만듭니다.
이미지에는 실행 JAR와 JRE가 들어가며 소스·빌드 도구·`.env`·호스트 데이터는 포함되지 않습니다.

## 문제 확인

- DB 연결 거부: `docker compose ps`와 `docker compose logs --tail 100 mariadb`로 DB 준비 상태를 확인합니다.
- DB 인증 실패: 기존 볼륨의 계정과 `.env`가 일치하는지 확인합니다. 볼륨 삭제로 해결하지 않습니다.
- 앱이 `unhealthy`: `docker compose logs --tail 100 app`과 readiness 응답을 확인합니다.
- 포트 충돌: 기존 프로세스를 확인하거나 `APP_PORT`/`DB_PORT`를 바꾸고 관련 URL도 함께 수정합니다.
- 다른 기기에서 접속: `APP_BIND_ADDRESS=0.0.0.0`, `APP_BASE_URL=http://<PC 주소>:<APP_PORT>`를 설정합니다.
- SMTP 실패: `logs/docker/kraft-email.log`를 확인합니다. 컨테이너에서 호스트 SMTP에 접속하려면
  Docker Desktop의 `host.docker.internal`을 사용합니다.
