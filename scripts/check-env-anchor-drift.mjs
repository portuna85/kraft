#!/usr/bin/env node
// OPS-ENV-01(docs/improvement.md): docker-compose.yml과 docker-compose.prod.yml은
// 각자 파일 안에서만 x-backend-common-env 앵커를 정의한다(YAML이 파일 간 앵커 공유를
// 지원하지 않아서) — 두 파일 헤더 주석이 "손으로 동기화하라"고 경고하지만 그걸
// 강제하는 검사가 없었다. 결과적으로 KRAFT_COMMUNITY_WRITE_RATE_LIMIT_PER_MINUTE·
// KRAFT_RETENTION_OPERATION_LOG_DAYS·KRAFT_RETENTION_ADMIN_AUDIT_LOG_DAYS·
// KRAFT_RETENTION_SAVED_NUMBER_LOCK_ORPHAN_DAYS 네 변수가 .env.example에는
// 문서화됐지만 application.yml의 기본값과 우연히 같아 증상 없이 어느 compose
// 파일의 environment:에도 실려 있지 않았다(scripts/check-env-drift.sh는 "코드에서
// 쓰는데 문서에 없는 것"만 잡는 단방향 검사라 이 사각지대를 못 잡는다).
//
// 이 스크립트는 두 가지를 본다.
// 1) application.yml/application-prod.yml이 실제로 참조하는 ${KRAFT_*}/${MARIADB_*}
//    변수 전체가 backend 서비스의 렌더링된 environment(docker compose config)에
//    dev/prod 양쪽 다 실려 있는지 — Spring이 바인딩을 시도하는 변수인데 컨테이너에
//    전달되지 않으면 "문서의 값을 바꿔도 아무 일도 안 일어나는" 사각지대가 된다.
// 2) 두 파일의 backend environment 키 집합 자체가 서로 같은지 — 한쪽에만 새 키를
//    추가하고 다른 쪽을 깜빡하는 미래의 드리프트를 잡는다.
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";

const REPO_ROOT = new URL("..", import.meta.url).pathname.replace(/^\/([A-Za-z]):/, "$1:");

// prod에만 있고 dev(local)에는 의도적으로 없는 키 — Flyway baseline/ddl-auto 재정의는
// 로컬 개발 편의용이고, prod는 application-prod.yml이 고정값을 쓰도록 의도적으로 env
// 오버라이드 경로를 열어두지 않는다.
const DEV_ONLY_ALLOWED = new Set([
  "KRAFT_FLYWAY_BASELINE_ON_MIGRATE",
  "KRAFT_FLYWAY_BASELINE_VERSION",
  "KRAFT_FLYWAY_ENABLED",
  "KRAFT_JPA_DDL_AUTO",
]);

function referencedSpringEnvVars() {
  const files = [
    "src/main/resources/application.yml",
    "src/main/resources/application-prod.yml",
  ];
  const vars = new Set();
  for (const file of files) {
    const text = readFileSync(new URL(file, `file://${REPO_ROOT}/`).pathname.replace(/^\/([A-Za-z]):/, "$1:"), "utf8");
    for (const match of text.matchAll(/\$\{(KRAFT_[A-Z0-9_]+|MARIADB_[A-Z0-9_]+)(?::|})/g)) {
      vars.add(match[1]);
    }
  }
  return vars;
}

function backendEnvKeys(composeArgs, envFileStub) {
  const stubPath = envFileStub
    ? new URL(envFileStub, `file://${REPO_ROOT}/`).pathname.replace(/^\/([A-Za-z]):/, "$1:")
    : null;
  const stubAlreadyExisted = stubPath ? existsSync(stubPath) : true;
  if (stubPath && !stubAlreadyExisted) writeFileSync(stubPath, "");

  let raw;
  try {
    raw = execFileSync("docker", ["compose", ...composeArgs, "config", "--format", "json"], {
      cwd: REPO_ROOT,
      encoding: "utf8",
      env: {
        ...process.env,
        // config 렌더링에만 필요한 더미 값 — 실제 시크릿과 무관
        // (check-container-hardening.mjs와 같은 패턴).
        MARIADB_ROOT_PASSWORD: "x", MARIADB_PASSWORD: "x", KRAFT_DB_PASSWORD: "x",
        KRAFT_DB_URL: "x", KRAFT_DB_USERNAME: "x",
        GRAFANA_ADMIN_PASSWORD: "x", KRAFT_ADMIN_ALLOWED_CIDR: "127.0.0.1/32",
        KRAFT_BACKEND_IMAGE_REF: "x", KRAFT_BACKEND_IMAGE_TAG: "x",
        KRAFT_WEB_IMAGE_REF: "x", KRAFT_WEB_IMAGE_TAG: "x",
        KRAFT_DOMAIN: "x", KRAFT_ADMIN_DOMAIN: "x", KRAFT_PUBLIC_BASE_URL: "http://x",
      },
    });
  } finally {
    if (stubPath && !stubAlreadyExisted) unlinkSync(stubPath);
  }
  const cfg = JSON.parse(raw);
  return new Set(Object.keys(cfg.services.backend.environment ?? {}));
}

const referenced = referencedSpringEnvVars();
const devKeys = backendEnvKeys(["-f", "docker-compose.yml", "--profile", "full"]);
const prodKeys = backendEnvKeys(["-f", "docker-compose.prod.yml"], ".env.community-oauth-flags");

const failures = [];

for (const key of [...referenced].sort()) {
  const inDev = devKeys.has(key);
  const inProd = prodKeys.has(key);
  if (!inDev && !inProd) {
    failures.push(`${key}: application.yml/application-prod.yml이 참조하지만 두 compose ` +
      "파일 어디에도 backend environment로 실려 있지 않다");
  } else if (!inDev) {
    failures.push(`${key}: docker-compose.yml의 backend environment에 없다(prod에는 있음)`);
  } else if (!inProd && !DEV_ONLY_ALLOWED.has(key)) {
    failures.push(`${key}: docker-compose.prod.yml의 backend environment에 없다(dev에는 있음)`);
  }
}

for (const key of devKeys) {
  if (!prodKeys.has(key) && !DEV_ONLY_ALLOWED.has(key)) {
    failures.push(`${key}: docker-compose.yml에만 있고 docker-compose.prod.yml에 없다 ` +
      "(DEV_ONLY_ALLOWED에도 없음 — 의도된 차이면 그 목록에 추가할 것)");
  }
}
for (const key of prodKeys) {
  if (!devKeys.has(key)) {
    failures.push(`${key}: docker-compose.prod.yml에만 있고 docker-compose.yml에 없다`);
  }
}

if (failures.length > 0) {
  console.error("ERROR: backend env anchor 드리프트:");
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log("OK: application.yml이 참조하는 KRAFT_*/MARIADB_* 변수가 dev/prod 양쪽 " +
  "backend environment에 모두 실려 있고, 두 파일의 키 집합도 일치한다(의도된 차이 제외)");
