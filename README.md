# Kraft

게시판과 로또 번호 추천을 함께 제공하는 서버 렌더링 웹 서비스입니다. 운영 주소는 https://kraft.io.kr 입니다.

- `/` 게시판: 글·댓글·이미지 첨부·신고, 이메일 인증 회원
- `/recommend` 번호 추천: 로그인 없이 쓰는 비저장 기능. 아래 [번호 추천 정책](#번호-추천-정책) 참고

## 기술 스택

- Java 25, Spring Boot 4, Spring Security, Spring Data JPA, Thymeleaf(서버 렌더링)
- Vue 3 아일랜드(화면의 일부만 마운트), Vite 빌드, 자체 호스팅 CSS·JS
- MariaDB + Flyway(마이그레이션 `src/main/resources/db/migration`), 세션은 Spring Session JDBC

## 로컬 실행

필요한 것: JDK 25, Node.js, Docker(개발용 MariaDB).

```
cp .env.example .env        # 값을 채운다. .env는 Git에서 제외된다
docker compose up -d --wait # 개발용 MariaDB만 띄운다
./gradlew bootRun           # Windows: .\gradlew.bat bootRun
```

`local` 프로파일이 프로젝트 루트의 `.env`를 읽습니다. 같은 이름의 OS 환경변수가 `.env`보다 우선합니다.

프런트 자원을 고칠 때:

```
npm ci
npm run build:vue   # src/vue → src/main/resources/static/js/vue-dist (결과물도 커밋한다)
npm run build:css   # src/styles → src/main/resources/static/css (결과물도 커밋한다)
```

## 환경 변수

값은 `.env.example`을 보고 채웁니다. 이름과 용도만 적습니다.

| 이름 | 용도 |
| --- | --- |
| `MARIADB_ROOT_PASSWORD`, `MARIADB_DATABASE`, `MARIADB_USER`, `MARIADB_PASSWORD` | docker-compose의 MariaDB 초기화 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_PORT` | 앱의 데이터소스 접속 정보 |
| `EMAIL_ENCRYPTION_KEY` | 회원 이메일 AES 암호화 키. prod 필수 |
| `EMAIL_HASH_PEPPER` | 이메일 조회용 HMAC-SHA256 pepper(16자 이상). prod 필수, 암호화 키와 다른 값 |
| `APP_BASE_URL` | 이메일 인증 링크·canonical·sitemap의 대표 주소 |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | 메일 발송(SMTP) |

`local` 프로파일은 암호화 키와 pepper가 없으면 개발용 기본값을 씁니다. `prod`에는 기본값이 없어 지정하지 않으면 기동이 실패합니다.

**두 값(암호화 키, pepper)은 백업과 분리해 따로 보관하세요.** 잃으면 저장된 이메일을 복호화하거나 조회할 수 없습니다. pepper는 한 번 정하면 바꾸지 않습니다.

## 테스트

```
./gradlew test          # 단위·통합. 기본은 H2 인메모리(test 프로파일)
npm run lint            # eslint + 타입 검사(JS, Vue)
npm run test:unit       # node --test
npm run check:vue       # 커밋된 vue-dist가 소스와 일치하는지
npm run check:css       # 커밋된 css가 소스와 일치하는지
npm run test:e2e        # Playwright
```

- 일부 테스트(MariaDB 마이그레이션·백업 복원 리허설 등)는 Testcontainers를 씁니다. Docker가 꺼져 있으면 건너뜁니다. CI에서는 모두 실행됩니다.
- E2E는 먼저 `./gradlew bootE2eJar`로 `build/libs/kraft-e2e.jar`를 만든 뒤 실행합니다.

## 배포

`main`에 푸시하면 GitHub Actions(`.github/workflows/build.yml`)가 테스트를 돌리고, 모두 통과하면 **테스트한 JAR 그대로** 운영 서버에 배포합니다. 즉 **`main` 푸시는 곧 운영 배포**입니다.

- 배포 스크립트: `deploy/deploy-apply.sh`(서버의 forced-command SSH로만 실행), 실패 시 롤백
- 서비스 정의: `deploy/kraft.service`(`/opt/kraft/app/.env`를 읽는다)
- 단일 인스턴스를 전제로 합니다. 인스턴스별 상태 목록은 `deploy/INSTANCE_STATE.md`

## 운영 도구

### 이메일 암호화 키 교체 (`rekey` 프로파일)

키가 유출되면 저장된 이메일을 새 키로 다시 암호화합니다.

1. **DB 백업을 먼저 받습니다.**
2. 앱을 내립니다.
3. 새 키를 `EMAIL_ENCRYPTION_KEY`, 옛 키를 `EMAIL_ENCRYPTION_KEY_OLD`로 지정하고 `EMAIL_HASH_PEPPER`도 함께 둔 채 실행합니다.
   ```
   java -jar kraft.jar --spring.profiles.active=rekey
   ```
4. 종료 코드 0(전체 재검증 통과)이면 `.env`의 `EMAIL_ENCRYPTION_KEY`가 새 키인 상태로 평소대로 기동합니다.

각 행은 복호화한 평문의 HMAC이 저장된 `email_hmac`과 같은지 대조한 뒤에만 다시 씁니다. 재실행해도 안전합니다.

### 백업·복구

`deploy/backup.sh`가 DB 덤프를 만들고 `deploy/verify-backup.sh`가 검증합니다. 백업에는 `EMAIL_ENCRYPTION_KEY`·`EMAIL_HASH_PEPPER` 값이 들어 있지 않습니다. 암호화 키는 식별자(지문)만 남깁니다.

## 번호 추천 정책

- 검증된 **과거 1등 6개 번호와 정확히 일치하는 조합**은 모든 전략에서 항상 제외합니다. 보너스 번호는 비교하지 않습니다. 검증된 이력이 준비되지 않았으면 건너뛰지 않고 명시적 오류로 거절합니다.
- 전략은 세 가지입니다.
  - `random`: 고정·제외·과거 1등 제외 조건을 지킨 허용 조합 안에서 무작위
  - `balanced`: 홀짝·저고·합계·연속쌍·구간 다섯 기준을 모두 채점
  - `reduce_shared_winner_risk`: 흔한 선택 패턴(고번호·한 자리수·5·7의 배수·연속)을 피하는 가정 기반 휴리스틱
- **당첨 확률을 높이는 기능이 아닙니다.** 추첨은 매번 독립이라 확률은 그대로입니다. 과거 1등 제외는 서비스가 정한 고정 선호 정책이며, 제외 후에는 전체 8,145,060개 조합에 대한 균등 선택이 아닙니다.
- 최신 회차는 동행복권의 비공식 endpoint에서 자동 수집합니다(추첨 뒤 주 4회 시도). 형식이 바뀌면 수집이 실패할 수 있고, 4회 연속 실패하면 관리자 알림 메일이 나갑니다.

## 개인정보와 보안

- 이메일은 AES로 암호화해 저장하고, 조회에는 별도 pepper로 만든 HMAC을 씁니다. 비밀번호는 해시로 저장합니다.
- 보안 취약점은 공개 이슈로 올리지 말고 저장소 소유자에게 직접 알려 주세요.

## 라이선스

소유자가 정하기 전까지 별도의 라이선스를 부여하지 않습니다.
