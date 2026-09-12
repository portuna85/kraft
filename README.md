# Kraft

Spring Boot 기반 커뮤니티 애플리케이션입니다. 로컬 실행에는 Java 25와 Docker Compose가 필요합니다.

## 로컬 실행

프로젝트 루트에서 실행합니다. PowerShell에서 `.env`가 없을 때만 예제 파일을 복사합니다.

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
```

`.env`에 실제 접속 정보를 입력합니다.

- `DB_URL`: `jdbc:mariadb://localhost:3306/<DB 이름>` 형식이며 DB 이름은 `MARIADB_DATABASE`와 맞춥니다.
- `DB_USERNAME`, `DB_PASSWORD`: 각각 `MARIADB_USER`, `MARIADB_PASSWORD`와 맞춥니다.
- 이메일 인증을 사용하려면 `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`도 설정합니다.

`.env`는 Java properties 형식으로 읽습니다. 예제처럼 따옴표나 `export` 없이 `KEY=value`를 사용합니다.
파일은 Git에서 제외되므로 실제 비밀번호를 저장소에 커밋하지 않습니다.

```powershell
docker compose up -d
docker compose ps
.\gradlew.bat bootRun
```

MariaDB가 `healthy` 상태가 된 뒤 앱을 실행하고 [http://localhost:8080](http://localhost:8080)에 접속합니다.
bash에서는 같은 위치에서 `./gradlew bootRun`을 사용합니다.

기본 프로파일인 `local`은 프로젝트 루트의 `.env`를 자동으로 읽습니다. 셸에서 별도로 값을 로드할 필요가 없습니다.
같은 이름의 OS 환경변수가 있으면 `.env`보다 우선하며, `.env` 없이 환경변수만 전달해도 됩니다.
DB 설정이 두 곳 모두에 없으면 앱 기동이 실패합니다. `prod`와 `test` 프로파일은 `.env`를 자동으로 읽지 않습니다.

현재 `local`의 `ddl-auto=create-drop` 설정은 앱 시작 시 테이블을 다시 만들고 정상 종료 시 삭제합니다.
기존 스키마와 데이터를 유지하면서 연결을 확인할 때는 다음 명령을 사용합니다. 스키마는 미리 준비되어 있어야 합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.jpa.hibernate.ddl-auto=validate --spring.session.jdbc.initialize-schema=never"
```

## IDE 실행

`com.kraft.KraftApplication`을 실행하고, 실행 구성의 작업 디렉터리를 프로젝트 루트로 지정합니다.
IntelliJ IDEA에서는 `Working directory`를 `$PROJECT_DIR$`로 설정합니다.
프로파일을 지정하지 않으면 `local`이 적용되며, 별도의 `.env` 플러그인이나 환경변수 복사 없이 파일을 읽습니다.
기존 실행 구성에 DB 환경변수가 남아 있으면 그 값이 `.env`보다 우선하므로 변경 시 함께 확인합니다.

## 테스트

```powershell
.\gradlew.bat test
```

Gradle이 `test` 프로파일을 활성화하고 `src/test/resources/application-test.yml`의 H2 인메모리 DB를 사용합니다.
테스트에는 Docker MariaDB나 `.env`가 필요하지 않습니다.

## 디자인 문서

화면 구성과 프론트엔드 구현 기록은 [docs/README.md](docs/README.md)를 참고합니다.
