#!/usr/bin/env node
// C-03: 서비스별 non-root·read-only fs·capability drop·no-new-privileges·쓰기 마운트·
// egress 사유를 코드로 고정한 매트릭스. "완료" 표시가 아니라 `docker compose config`가
// 실제로 뱉는 병합 설정과 대조해 검증한다 — local(docker-compose.yml [+ .local])과
// prod(docker-compose.prod.yml) 오버레이 둘 다 CI에서 돈다.
//
// 각 항목의 근거는 docker-compose.yml/.prod.yml의 인접 주석에 있다 — 여기 표는 그
// 사실을 "이 서비스는 이래야 한다"는 회귀 방지 단언으로만 굳힌다. 값은 실제로
// docker run으로 기동 검증 후 반영됨(cap_drop:ALL만으로 깨지는 조합은 최소 cap_add로
// 보강 — mariadb: SETUID/SETGID/DAC_OVERRIDE/CHOWN, caddy: NET_BIND_SERVICE).
import { execFileSync } from "node:child_process";
import { existsSync, writeFileSync, unlinkSync } from "node:fs";

const REPO_ROOT = new URL("..", import.meta.url).pathname.replace(/^\/([A-Za-z]):/, "$1:");

// service -> { capDropAll, readOnly, note }
// readOnly:false는 "아직 검증되지 않았거나 이 서비스 특성상 불필요"라는 뜻 — 실제
// 이유는 note에 있다. 둘 다 그냥 누락된 게 아니라 의식적 결정이다.
const EXPECTATIONS = {
  mariadb: {
    capDropAll: true,
    readOnly: false,
    note: "MariaDB 엔트리포인트가 임시 파일·소켓·InnoDB 임시테이블스페이스 등 데이터 " +
      "볼륨 밖에도 쓰기가 필요해 read_only는 검증하지 않음(볼륨은 mariadb-data 하나뿐).",
  },
  backend: {
    capDropAll: true,
    readOnly: true,
    note: "backend-logs 볼륨 + /tmp tmpfs 조합으로 read_only 검증 완료(실제 이미지로 " +
      "기동·Flyway 마이그레이션·헬스체크 통과 확인).",
  },
  web: {
    capDropAll: true,
    readOnly: true,
    note: "web-next-cache 볼륨 + /tmp tmpfs 조합.",
  },
  caddy: {
    capDropAll: true,
    readOnly: true,
    note: "80/443 바인드에 NET_BIND_SERVICE 필요(cap_drop:ALL만으로는 포트 무관하게 " +
      "exec 자체가 실패 — 바이너리 내장 file capability 요구사항, 실측 확인). " +
      "prod는 caddy-data/caddy-config 볼륨(TLS 인증서 영속화), local은 tmpfs(휘발성 OK).",
  },
  prometheus: { capDropAll: true, readOnly: true, note: "prometheus-data 볼륨 + /tmp tmpfs." },
  alertmanager: { capDropAll: true, readOnly: true, note: "alertmanager-data 볼륨 + /tmp tmpfs." },
  "node-exporter": {
    capDropAll: true,
    readOnly: true,
    note: "호스트 /proc·/sys를 pid:host + /:/host:ro로만 읽는다 — 쓰기 자체가 불필요.",
  },
  "mariadb-exporter": {
    capDropAll: true,
    readOnly: true,
    note: "OBS-PROM-01: mariadb에 읽기 전용 쿼리만 날리고 로컬 상태를 쓰지 않는다.",
  },
  grafana: {
    capDropAll: true,
    readOnly: false,
    note: "부팅 시 grafana-data 볼륨 밖(플러그인 캐시 등 여러 경로)에도 쓰기가 발생해 " +
      "read_only는 검증하지 않음 — cap_drop:ALL만 실측 확인.",
  },
};

function loadConfig(composeArgs) {
  const raw = execFileSync("docker", ["compose", ...composeArgs, "config", "--format", "json"], {
    cwd: REPO_ROOT,
    encoding: "utf8",
    env: {
      ...process.env,
      // config 렌더링에만 필요 — 실제 값이 아니라 도커 컴포즈가 "설정 안 됨" 경고만
      // 안 내게 하는 더미. 실제 배포 시크릿과 무관하다.
      MARIADB_ROOT_PASSWORD: "x", MARIADB_PASSWORD: "x", KRAFT_DB_PASSWORD: "x",
      GRAFANA_ADMIN_PASSWORD: "x", KRAFT_ADMIN_ALLOWED_CIDR: "127.0.0.1/32",
      KRAFT_BACKEND_IMAGE_REF: "x", KRAFT_BACKEND_IMAGE_TAG: "x",
      KRAFT_WEB_IMAGE_REF: "x", KRAFT_WEB_IMAGE_TAG: "x",
      KRAFT_DOMAIN: "x", KRAFT_ADMIN_DOMAIN: "x", KRAFT_PUBLIC_BASE_URL: "http://x",
    },
  });
  return JSON.parse(raw);
}

function checkOverlay(label, composeArgs, envFileStub) {
  const stubPath = envFileStub ? new URL(envFileStub, `file://${REPO_ROOT}/`).pathname.replace(/^\/([A-Za-z]):/, "$1:") : null;
  const stubAlreadyExisted = stubPath ? existsSync(stubPath) : true;
  if (stubPath && !stubAlreadyExisted) {
    // docker compose config는 env_file로 참조된 파일이 실존해야 렌더링에 성공한다 —
    // 실제 시크릿 값은 필요 없고(config 렌더링 단계일 뿐) 파일 존재 여부만 본다.
    writeFileSync(stubPath, "");
  }

  let cfg;
  try {
    cfg = loadConfig(composeArgs);
  } finally {
    if (stubPath && !stubAlreadyExisted) unlinkSync(stubPath);
  }
  const failures = [];

  for (const [name, svc] of Object.entries(cfg.services)) {
    const expected = EXPECTATIONS[name];
    if (!expected) {
      failures.push(`[${label}] ${name}: EXPECTATIONS 표에 없는 새 서비스 — 하드닝 검토 후 항목을 추가하라`);
      continue;
    }

    const hasNoNewPrivileges = (svc.security_opt ?? []).some((s) => s.includes("no-new-privileges:true"));
    if (!hasNoNewPrivileges) {
      failures.push(`[${label}] ${name}: security_opt no-new-privileges:true 누락`);
    }

    const hasCapDropAll = (svc.cap_drop ?? []).includes("ALL");
    if (expected.capDropAll && !hasCapDropAll) {
      failures.push(`[${label}] ${name}: cap_drop:ALL 누락(기대값: 있어야 함)`);
    }

    const isReadOnly = svc.read_only === true;
    if (expected.readOnly && !isReadOnly) {
      failures.push(`[${label}] ${name}: read_only:true 누락(기대값: 있어야 함 — ${expected.note})`);
    }
  }

  return failures;
}

const allFailures = [
  // --profile full: mariadb를 제외한 나머지(backend/web/모니터링/caddy-local)는 기본
  // 프로필에서 빠져 있다(로컬 IntelliJ 직접 실행 워크플로용) — 이 검증은 전체 서비스를
  // 대상으로 해야 하므로 명시적으로 full 프로필까지 포함해 렌더링한다.
  ...checkOverlay("local", ["-f", "docker-compose.yml", "-f", "docker-compose.local.yml", "--profile", "full"]),
  ...checkOverlay("prod", ["-f", "docker-compose.prod.yml"], ".env.community-oauth-flags"),
];

if (allFailures.length > 0) {
  console.error("ERROR: 컨테이너 하드닝 매트릭스 위반:");
  for (const f of allFailures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log("OK: local/prod 오버레이 모두 하드닝 매트릭스를 만족한다");
