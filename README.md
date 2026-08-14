# KRAFT Lotto

KRAFT Lotto는 로또 6/45 당첨 데이터 조회와 통계, 번호 추천, 저장 번호 확인, OAuth2 커뮤니티를 한곳에서 제공하는 웹 서비스입니다.

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](#기술-구성)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)](#기술-구성)
[![Next.js](https://img.shields.io/badge/Next.js-16.3.0-black?logo=nextdotjs&logoColor=white)](#기술-구성)
[![React](https://img.shields.io/badge/React-19.2.8-149ECA?logo=react&logoColor=white)](#기술-구성)
[![MariaDB](https://img.shields.io/badge/MariaDB-11.7-003545?logo=mariadb&logoColor=white)](#기술-구성)

- 운영 서비스: [https://kraft.io.kr](https://kraft.io.kr/)

Google·Naver 로그인부터 세션 유지, 게시글·댓글·답글 작성, 수정·삭제, 로그아웃까지 실제 브라우저 사용자 흐름을 검증했습니다.

> 번호 추천은 통계를 참고하는 기능입니다. 모든 로또 6/45 조합의 1등 당첨 확률은 동일하며, 서비스는 당첨을 보장하지 않습니다.

## 제공 기능

### 로또 데이터

- 최신·과거 당첨 회차의 데이터 신선도 조회
- 번호별 빈도, 홀짝·고저·합계 구간, 동반 출현 통계
- 임의 번호와 빈도 TOP/BOTTOM 조합의 역대 1등 당첨 회차·추첨일·당첨금 표시
- 제외 번호와 공동 당첨 위험 완화 옵션을 반영한 조합 추천
- 과거 1등 조합과 추천 조합의 중복 검사
- 익명 기기 토큰 기반 번호 저장과 회차별 당첨 결과 확인
- 공개 서비스 상태와 최근 수집·보정 이력 제공

### 커뮤니티

- Google·Naver OAuth2 로그인
- 게시글 작성, 조회, 수정, 삭제
- 댓글과 답글 2단계 구조
- 탈퇴한 사용자의 게시물은 문맥을 보존한 상태로 삭제되는 tombstone 정책
- CSRF 보호, 작성자 권한 검사, 신고·제한

### 운영

- 최신 회차 자동 수집과 수동 백필
- 통계 요약 사전 계산과 데이터 reconciliation
- 감사 로그와 운영 API
- Prometheus, Grafana, Alertmanager 관측
- 배포 후 API·페이지·OAuth redirect 스모크 테스트
- 이미지 취약점 검사, SBOM, provenance, CodeQL

## 기술 구성

| 영역 | 기술 |
| --- | --- |
| 백엔드 | Java 25, Spring Boot 4.1.0, Spring Security, OAuth2 Client, Spring Data JPA, Validation, Actuator, Thymeleaf |
| 데이터 | MariaDB 11.7, Flyway V1~V33, H2, Caffeine |
| 복원력 | Virtual Threads, ShedLock, Resilience4j, 트랜잭션 이벤트 |
| 프론트엔드 | Next.js 16.3.0 App Router, React 19.2.8, TypeScript 6.0.3, ISR, CSP nonce |
| 테스트 | JUnit 5, Testcontainers, JaCoCo, Checkstyle, SpotBugs, Vitest, Testing Library, Playwright |
| 인프라 | Docker Compose, Caddy, Prometheus, Grafana, Alertmanager, GHCR |
| 자동화 | GitHub Actions, CodeQL, Dependabot, Trivy, SBOM·provenance |

## 서비스 구조

```mermaid
flowchart LR
    User["브라우저"] -->|"HTTPS"| Edge["Caddy"]
    Edge -->|"페이지 · 정적 자산"| Web["Next.js :3000"]
    Edge -->|"/api/v1/* · OAuth"| API["Spring Boot :8080"]
    API --> DB[("MariaDB")]
    API -->|"커밋 후 revalidate"| Web
    API -.->|"선택적 회차 수집"| External["외부 로또 데이터"]
    Prometheus --> API
    Grafana --> Prometheus
    Prometheus --> Alertmanager
```

Caddy만 외부 요청을 받습니다. 페이지와 정적 자산은 Next.js로, `/api/v1/*`, `/oauth2/*`, `/login/*`, `/logout`은 Spring Boot로 전달합니다. 관리자·운영·Actuator 경로는 공개 도메인에서 차단하며, 관리자 도메인과 IP allowlist로 별도 보호합니다.

### 요청과 데이터 흐름

1. 자동 수집기가 최신 당첨 회차를 확인하고 실패 시 제한적으로 재시도합니다.
2. 수집과 수동 보정은 idempotent upsert와 동시성 가드를 통과합니다.
3. 커밋 후 통계 요약을 갱신하고 관련 Next.js ISR 경로를 무효화합니다.
4. reconciliation은 원본 회차와 통계 요약을 최신 상태로 비교해 차이를 복구합니다.
5. 운영 로그와 감사 로그는 보존 기간에 따라 정리합니다.

HTTP 접근 로그는 정상(2xx/3xx) 요청을 DEBUG에서만 기록하며 이 메시지에는 클라이언트 IP를
넣지 않습니다. 조사 가치가 있는 4xx/5xx는 요청 ID, 메서드, 경로, 상태, 처리 시간과 신뢰된
프록시 기준 클라이언트 IP를 WARN/ERROR로 기록합니다. 쿼리 문자열, 인증 헤더, 쿠키, 토큰,
요청 본문은 기록하지 않습니다. 운영 파일 로그는 최대 30일, 전체 1GB(오류 전용 200MB)로
롤링되며 먼저 도달한 한도를 적용합니다.

## 보안 모델

Spring Security 필터 체인은 범위가 좁은 체인부터 다음 순서로 적용합니다.

1. 로컬 H2 콘솔: 로컬 프로필 전용
2. 관리자: `/admin/**`, 세션 로그인, CSRF, 동시 세션 1개
3. 커뮤니티: `/api/v1/community/**`, OAuth2 세션, double-submit CSRF
4. 공개·운영 API: `/api/**`, `/actuator/**`, `/ops/**`, stateless

운영 커뮤니티 쿠키에는 `Secure`, `HttpOnly`, `SameSite=Lax`, host-only 속성을 적용합니다. 세션에는 OAuth 공급자의 토큰이나 원본 프로필을 보관하지 않고 내부 사용자 ID만 유지합니다. 개인 응답은 `private, no-store`이며 공개 ISR HTML에는 사용자 상태를 포함하지 않습니다.

상태 변경 요청은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 전송해야 합니다. 미인증 요청은 `401`, CSRF 검증 실패는 `403`으로 구분합니다.

## 저장소 구성

```text
src/main/java/com/kraft/
  admin/          관리자 로그인, 회차 수집, 백필, 감사 로그
  common/         설정, 보안, 오류 계약, 공통 로또 규칙
  community/      OAuth2, 사용자, 게시글, 댓글
  operationlog/   수집·보정 이력과 공개 인시던트
  ops/            운영 API와 서비스 상태
  recommend/      번호 추천과 과거 당첨 조합 배제
  saved/          기기 토큰 기반 저장 번호
  statistics/     통계 계산과 요약
  winningnumber/  회차 조회, 외부 수집, 자동 수집

src/main/resources/
  db/migration/   Flyway V1~V33
  templates/      관리자 Thymeleaf 화면

web/
  src/app/        Next.js 페이지와 Route Handler
  src/components/ 화면 구성과 클라이언트 상태
  src/lib/        API, 검증, 분석, CSRF, 로깅
  e2e/            기본·콘텐츠·광고·Caddy 프록시 게이트웨이 Playwright 테스트
caddy/            운영·로컬 동일 출처 라우팅
infra/            모니터링과 경보 설정
scripts/          개발, 검증, 배포, 롤백, 백업·복구
```

## 로컬 실행

### 준비 사항

- JDK 25
- Node.js 24 이상과 npm
- Docker Desktop 또는 Docker Engine
- Bash 스크립트 실행이 필요한 경우 Git Bash 또는 WSL

### Local backend

```powershell
.\scripts\dev-backend.ps1
```

첫 실행에서는 `.env.local.example`을 `.env.local`로 복사하고 종료합니다. 생성된 환경 파일을 검토한 다음 같은 명령을 다시 실행합니다.

- 백엔드: <http://localhost:8080>
- H2 콘솔: <http://localhost:8080/h2-console>
- 로컬 관리자 기본값: `admin` / `admin`

기본 관리자 계정은 로컬 개발 전용입니다.

### Local frontend

백엔드를 먼저 실행한 뒤 다른 PowerShell에서 시작합니다.

```powershell
.\scripts\dev-web.ps1
```

- 프론트엔드: <http://localhost:3000>
- 기본 백엔드 주소: `http://localhost:8080`

`next dev` 단독 실행은 서버 컴포넌트가 백엔드를 직접 호출하는 읽기 전용 페이지(홈·통계·빈도 등)만
정상 동작합니다. `/api/v1/*`를 쓰는 브라우저 측 호출(추천 생성, 저장 번호, 커뮤니티 글쓰기·좋아요
등)은 이 모드에서 프록시할 경로가 없어 전부 실패합니다(KF-06 — `web/src/app/api/v1/**` 라우트
핸들러를 제거하고 아래 "로컬 개발 표준 게이트웨이"로 일원화했습니다). 이런 기능을 다루려면 아래
Caddy 게이트웨이로 전체 스택을 띄우세요.

### 전체 Docker Compose

```powershell
Copy-Item .env.example .env
```

`.env`에 DB 비밀번호, Ops 토큰, revalidate secret, Grafana 비밀번호를 설정합니다. 그다음 Git Bash 또는 WSL에서 로컬용 Alertmanager 설정을 만들고 서비스를 시작합니다.

```bash
ALERTING_DISABLED=true bash scripts/deploy/render-alertmanager.sh
docker compose --profile full up -d --build
```

`mariadb`만 필요하면(백엔드/프론트를 IntelliJ 등에서 직접 실행하는 경우) `--profile full` 없이 `docker compose up -d`만 실행합니다 — backend/web/모니터링/Caddy는 기본 프로필에서 빠져 있습니다.

`ALERTING_DISABLED=true`는 로컬·스테이징에서만 사용합니다. 운영에서는 실제 경보 webhook을 설정해야 합니다.

`infra/alertmanager/alertmanager.yml`은 Git에 커밋하지 않으므로(clean clone에는 없음) 렌더 스크립트보다 `docker compose up`을 먼저 실행하면 Docker가 그 경로를 빈 디렉터리로 자동 생성해버립니다. 이후 렌더 스크립트를 실행하면 "Is a directory" 오류로 실패합니다. 이 경우 `rmdir infra/alertmanager/alertmanager.yml` 실행 후 렌더 스크립트를 다시 실행하세요.

`scripts/deploy/up-full-stack.sh`는 렌더(Caddy 포함) 기동부터 smoke test를 한 번에 실행하는 로컬 전용 편의 스크립트입니다. smoke test는 Caddy가 만드는 단일 진입점(`http://localhost`)이 실제로 동작하는지 확인합니다 — 아래 "로컬 개발 표준 게이트웨이"와 같은 `docker-compose.local.yml` 조합으로 띄웁니다. clean clone 직후 전체 스택이 정상 동작하는지 한 번에 확인하고 싶을 때 사용합니다.

## 로컬 개발 표준 게이트웨이

`/api/v1/*`를 실제로 쓰는 개발(추천·저장 번호·커뮤니티·OAuth2 로그인 등)은 web:3000과
backend:8080을 하나의 동일 출처(`http://localhost`)로 묶는 로컬 Caddy(`caddy/Caddyfile.local`)
를 표준 진입점으로 씁니다. `web/src/app/api/v1/**`의 Next 라우트 핸들러가 이 역할을 대신하던
시절(→ KF-06)에는 `next dev` 단독 실행만으로도 됐지만, 그 핸들러가 제거된 지금은 이 방법이 유일한
경로입니다.

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml --profile full up -d --build
```

접속 주소는 반드시 <http://localhost>를 사용합니다. OAuth2 공급자 callback과 세션 쿠키도 이
경로로만 운영과 동일하게 재현됩니다 — `.env`에 사용할 공급자의 ID와 secret을 넣고 해당 Spring
profile을 활성화합니다.

```dotenv
SPRING_PROFILES_ACTIVE=local,community-google-oauth,community-naver-oauth
KRAFT_COMMUNITY_GOOGLE_CLIENT_ID=
KRAFT_COMMUNITY_GOOGLE_CLIENT_SECRET=
KRAFT_COMMUNITY_NAVER_CLIENT_ID=
KRAFT_COMMUNITY_NAVER_CLIENT_SECRET=
```

공급자 콘솔에 등록할 callback은 다음과 같습니다.

```text
http://localhost/login/oauth2/code/google
http://localhost/login/oauth2/code/naver
```

운영에서는 같은 경로에 `https://kraft.io.kr` 주소를 등록합니다. Google은 공개 프로필 범위만 사용하고, Naver 사용자 별칭은 `nickname`만 저장합니다. ID와 secret은 반드시 시크릿 값으로 설정하며 저장소에는 커밋하지 않습니다.

## 주요 API

### 공개·개인 API

| Method | Endpoint | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/rounds/latest` | 최신 당첨 회차 |
| `GET` | `/api/v1/rounds/freshness` | 데이터 신선도 |
| `POST` | `/api/v1/numbers/recommend` | 추천 조합 생성 |
| `GET` | `/api/v1/numbers/check` | 과거 1등 조합과 상세 당첨 내역 검사 |
| `GET` | `/api/v1/stats/frequency` | 번호별 빈도 |
| `GET` | `/api/v1/stats/patterns` | 홀짝·고저·합계 패턴 |
| `GET` | `/api/v1/stats/companion` | 동반 출현 통계 |
| `POST` | `/api/v1/stats/analysis` | 임의 번호 조합 통계와 역대 1등 당첨 내역 분석 |
| `GET`, `POST`, `DELETE` | `/api/v1/saved/**` | 저장 번호 관리 |
| `GET` | `/api/v1/status`, `/api/v1/status/incidents` | 서비스 상태와 공개 이력 |

저장 번호 API는 인증 계정 대신 익명 기기 토큰을 사용합니다.

### 커뮤니티 API

| Method | Endpoint | 접근 조건 |
| --- | --- | --- |
| `GET` | `/api/v1/community/session` | 선택적 세션 |
| `GET` | `/api/v1/community/posts`, `/posts/{id}` | 공개 |
| `POST`, `PUT`, `DELETE` | `/api/v1/community/posts/**` | OAuth2 세션 + CSRF |
| `GET` | `/api/v1/community/posts/{id}/comments` | 공개 |
| `POST` | `/api/v1/community/posts/{id}/comments` | OAuth2 세션 + CSRF |
| `DELETE` | `/api/v1/community/comments/{id}` | OAuth2 세션 + CSRF |
| `POST` | `/api/v1/community/me/withdrawal` | OAuth2 세션 + CSRF |

탈퇴(`me/withdrawal`)는 영구 삭제입니다. `CommunityWithdrawalService`가 좋아요·북마크·신고·차단을 지우고, 게시글·댓글 본문을 `eraseForAccountDeletion()`으로 비운 뒤(다른 사용자의 대화 구조만 남습니다), 등록된 `AccountDataDeletionHandler`(추천 이력·기기 클레임)를 모두 호출하고 마지막으로 `community_users` 행을 삭제합니다. 재활성화는 없으며, 같은 OAuth 계정으로 다시 로그인하면 완전히 새 계정이 만들어집니다. 남아 있는 다른 기기의 세션은 `CommunityWithdrawnAccountFilter`가 다음 요청에서 계정 부재를 확인하고 `COMMUNITY_ACCOUNT_DELETED` 401로 끊습니다.

`/ops/**`는 `X-Ops-Token`이 필요합니다. `/admin/**`은 별도 관리자 도메인의 세션 인증과 IP allowlist를 사용합니다.

## 환경 파일

| 파일 | 용도 |
| --- | --- |
| `.env.local.example` | Spring Boot 직접 실행 |
| `.env.example` | 기본·로컬 Docker Compose |
| `.env.prod.example` | 운영 CD 환경 렌더링 |
| `web/.env.local.example` | Next.js 직접 실행 |
| `web/.env.example` | Next.js 컨테이너 빌드 |

실제 `.env*`, OAuth secret, DB 비밀번호, Ops 토큰은 커밋하지 않습니다.

## 검증

### 백엔드

```powershell
.\gradlew.bat clean check bootJar `
  -PstrictStatic=true `
  -PstrictCoverage=true `
  --console=plain
```

테스트 수치는 시점에 따라 바뀌므로 위 명령을 직접 실행해 확인하세요. `check` 태스크가 실패·오류·skip 없이 통과하고 JaCoCo 커버리지 게이트를 넘기면 정상입니다.

### 프론트엔드

```powershell
Set-Location web
npm ci
npm run lint
npm run typecheck
npm run test:coverage
npm run build
npm run test:e2e
npm run test:e2e:content
```

Vitest 테스트 수와 커버리지 수치도 위 `npm run test:coverage` 실행 결과로 확인하세요.

고정 광고 오버레이는 별도 build 디렉터리와 환경값으로 검증합니다.

```powershell
$env:NEXT_DIST_DIR = ".next-ad-overlay"
$env:NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY = "DAN-local-overlay-test"
npm run build
npm run test:e2e:ad-overlay
Remove-Item Env:NEXT_DIST_DIR
Remove-Item Env:NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY
```

## CI/CD

| 워크플로 | 역할 |
| --- | --- |
| `ci.yml` | 백엔드·프론트엔드 빌드와 테스트, 정적 분석, E2E, Caddy 검증, 이미지 게시·스캔 |
| `pr.yml` | PR 의존성 취약점 검사 |
| `codeql.yml` | Java와 JavaScript/TypeScript CodeQL |
| `cd.yml` | 성공한 `main` CI의 정확한 SHA 배포와 실패 시 rollback |

배포 파이프라인은 필수 환경값과 OAuth 자격 증명 셋을 검증하고, GHCR 이미지 digest로 서비스를 기동합니다. readiness, 핵심 API와 페이지, Google·Naver 로그인 redirect, Flyway 버전을 확인하며 실패 시 이전 이미지로 되돌립니다.

CI/CD 실행 이력은 GitHub Actions 탭에서 확인할 수 있습니다.

## 운영 명령

```bash
# 운영 환경 렌더링과 검증
bash scripts/deploy/render-env.sh .env.prod.example .env.prod
set -a
source .env.prod
set +a
bash scripts/deploy/validate-env.sh

# 백업과 복구 연습
bash scripts/db-backup.sh
bash scripts/caddy-data-backup.sh
bash scripts/db-restore-drill.sh

# 배포 후 스모크 테스트
bash scripts/deploy/smoke-test.sh
```

`scripts/archive/migrate-2026-06/`은 완료된 이관 데이터 이전 기록입니다. 현재 운영 도구로 사용하지 않으며, 남은 주의사항은 통합 기술 문서에 기록되어 있습니다.

## 문서 정책

프로젝트 문서는 `README.md` 하나만 유지합니다. 서비스 소개, 실행, API, 검증, 배포 관련 설명은 이 파일에서 공동 관리합니다.

README 외의 독립 문서(가이드·계획서·명세서·리포트·ADR 등)는 만들지 않습니다. 설명이 필요하면 이 README에 통합하거나 코드 주석으로 남깁니다.
