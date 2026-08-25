// 라우트별 First Load JS를 측정해 예산과 비교한다.
//
// 측정 근거(레거시에서 얻은 환경 지식): Turbopack 빌드는 webpack 시절의
// "Route (app) … First Load JS" 표를 콘솔에 찍지 않는다. 대신
// `.next/server/app/**/page_client-reference-manifest.js`의
// entryJSFiles["[project]/src/app/<route>/page"]가 그 라우트가 실제로 내려받는 JS
// 청크 목록이고, 이 배열은 공용 레이아웃 청크를 이미 포함한다. 클라이언트 컴포넌트가
// 하나도 없는 순수 RSC 라우트는 자기 키가 아예 없으므로, 그 경우 레이아웃 단독 크기를
// 바닥값으로 쓴다 — 안 그러면 그 라우트가 측정에서 통째로 빠진다.
//
// 측정 단위는 gzip이다("Initial route JS <=180KB gzip"). 브라우저가 실제로 받는
// 바이트는 원본이 아니라 서버가 압축해 보내는 바이트라,
// raw byte로 재면 목표치와 비교 자체가 안 맞는다. `maxKB`(목표)는 이번에 같이
// 재산정하지 않는다 — codex도 "구현 후 측정해 확정한다"고 해 뒀으니, 실측치를 몇 번
// 더 쌓은 뒤 별도로 판단한다.
//
// 순수 계산부(허용치·키 해석·공용 청크 분류·판정)는 bundle-budget-core.mjs에 있다.
//
//   npm run build && npm run budget:bundle          측정 + 예산 비교
//   node scripts/bundle-budget.mjs --save-baseline  이번 측정치를 예산으로 저장
import { existsSync, globSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { gzipSync } from "node:zlib";

import {
  entryPathOf,
  evaluateRoute,
  ownEntryKey,
  toKB,
  routeOf,
  sharedChunkSet,
  splitSizes,
} from "./bundle-budget-core.mjs";

const webDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const distDir = path.join(webDir, process.env.NEXT_DIST_DIR ?? ".next");
const appServerDir = path.join(distDir, "server", "app");
const budgetPath = path.join(webDir, "scripts", "bundle-budget.json");
const resultsPath = path.join(webDir, "scripts", "bundle-results.json");
const saveBaseline = process.argv.includes("--save-baseline");

if (!existsSync(appServerDir)) {
  console.error(`ERROR: ${appServerDir}가 없습니다 — 먼저 "npm run build"를 실행하세요.`);
  process.exit(1);
}

function readManifest(file) {
  const source = readFileSync(file, "utf8");
  const body = source.slice(source.indexOf("= {") + 2).trim();
  return JSON.parse(body.endsWith(";") ? body.slice(0, -1) : body);
}

function manifestFiles() {
  // route_…(API·robots·sitemap·아이콘·OG)는 브라우저가 렌더하는 페이지가 아니고,
  // _global-error는 Next 내부 래퍼라 둘 다 예산 대상이 아니다.
  return globSync("**/page_client-reference-manifest.js", { cwd: appServerDir }).filter(
    (file) => !file.replaceAll("\\", "/").startsWith("_global-error"),
  );
}

/** gzip 압축 후 바이트 — 브라우저가 실제로 내려받는 크기(codex §17.4). 청크마다 1회만 잰다. */
const chunkSizeCache = new Map();
function chunkBytes(chunk) {
  if (!chunkSizeCache.has(chunk)) {
    const file = path.join(distDir, chunk);
    chunkSizeCache.set(chunk, existsSync(file) ? gzipSync(readFileSync(file)).length : 0);
  }
  return chunkSizeCache.get(chunk);
}

/** 델타를 부호와 함께 — "+1.5"/"−"가 아니라 "0"도 그대로 보이게 한다. */
function signed(value) {
  return value > 0 ? `+${value}` : `${value}`;
}

/**
 * 이 라우트가 클라이언트 컴포넌트를 하나도 안 가져도 받는 JS — 자기 레이아웃 체인이다.
 * 전역에서 하나만 구해 공유하면 셸이 갈라진 구조(공개/세션/운영)에서 틀린 값이 된다.
 */
function layoutChunksOf(entries) {
  const layoutKeys = Object.keys(entries).filter((key) => key.endsWith("/layout"));
  return layoutKeys.flatMap((key) => entries[key] ?? []);
}

/**
 * icon.tsx/apple-icon.tsx/manifest.ts(메타데이터 라우트)를 추가하면서 Turbopack이
 * `[project]/src/app/icon--metadata`라는 전역 공유 entry를 새로 만든다 — 이 청크
 * (사실상 프레임워크 공용 런타임)는 모든 페이지의 `<link rel="icon">`이
 * 참조하므로 실제로는 전 라우트가 받는다. 그런데 각 페이지 자신의 entryJSFiles
 * 목록에는 더 이상 포함되지 않아(예전에는 페이지 청크에 이미 섞여 있었다), 레이아웃
 * 청크처럼 별도로 더해 주지 않으면 모든 라우트가 실제보다 훨씬 작게 측정된다.
 */
function iconMetadataChunksOf(entries) {
  return entries["[project]/src/app/icon--metadata"] ?? [];
}

const measured = {};
const degraded = [];

for (const file of manifestFiles()) {
  const entries = readManifest(path.join(appServerDir, file)).entryJSFiles ?? {};
  const entryPath = entryPathOf(file);
  const route = routeOf(entryPath);
  const ownKey = ownEntryKey(entryPath, entries);
  const chunks = ownKey ? (entries[ownKey] ?? []) : [];

  // 키를 못 찾았다는 것은 측정이 바닥값으로 퇴화했다는 뜻이다 — 조용히 넘기면 "예산
  // 통과"가 거짓말이 된다. entry가 아예 없는 순수 RSC 라우트만 바닥값을 허용하고,
  // entry가 있는데 자기 키만 못 찾은 경우는 측정 버그이므로 실패시킨다(PERF-BUNDLE-01).
  if (!ownKey && Object.keys(entries).length > 0) {
    degraded.push({ route, keys: Object.keys(entries) });
  }

  const effective = [
    ...(chunks.length > 0 ? chunks : layoutChunksOf(entries)),
    ...iconMetadataChunksOf(entries),
  ];
  measured[route] = { chunks: [...new Set(effective)] };
}

// 공용/전용 분류는 전 라우트를 다 본 뒤에야 가능하다 — 청크가 몇 개 라우트에 등장하는지가
// 기준이기 때문이다.
const sharedChunks = sharedChunkSet(
  Object.fromEntries(Object.entries(measured).map(([route, info]) => [route, info.chunks])),
);

for (const info of Object.values(measured)) {
  const { sharedKB, routeKB } = splitSizes(info.chunks, sharedChunks, chunkBytes);
  // 총합은 부분의 합이 아니라 바이트 합에서 한 번에 낸다 — 부분마다 반올림한 뒤 더하면
  // 기존 기준선과 0.1 KB씩 어긋나 가짜 회귀가 생긴다.
  info.totalKB = toKB(info.chunks.reduce((sum, chunk) => sum + chunkBytes(chunk), 0));
  info.sharedKB = sharedKB;
  info.routeKB = routeKB;
  info.chunkCount = info.chunks.length;
}

console.log("라우트별 초기 클라이언트 JS, gzip 기준 (내림차순):");
for (const [route, info] of Object.entries(measured).sort((a, b) => b[1].totalKB - a[1].totalKB)) {
  console.log(
    `  ${String(info.totalKB).padStart(7)} KB  (공용 ${info.sharedKB} + 전용 ${info.routeKB}, ` +
      `청크 ${info.chunkCount}개)  ${route}`,
  );
}

if (degraded.length > 0) {
  for (const { route, keys } of degraded) {
    console.error(
      `\n  ERROR ${route}: 자기 entry 키를 찾지 못해 바닥값으로 측정됐습니다 — ` +
        "예산이 무의미해집니다. bundle-budget-core.mjs의 ownEntryKey를 고치세요.\n" +
        `        manifest 키: ${keys.join(", ")}`,
    );
  }
}

writeFileSync(
  resultsPath,
  `${JSON.stringify({ measuredAt: new Date().toISOString(), routes: measured }, null, 2)}\n`,
);
console.log(`\n결과 저장: ${resultsPath}`);

if (saveBaseline) {
  // 퇴화한 측정을 기준선으로 굳히면 그 라우트는 영원히 게이트 밖에 남는다 —
  // 실제로 /_not-found가 그렇게 4.3 KB에 묶여 있었다.
  if (degraded.length > 0) {
    console.error("\n퇴화한 측정이 있어 기준선을 갱신하지 않습니다. 먼저 위 ERROR를 해결하세요.");
    process.exit(1);
  }

  const budgetFile = JSON.parse(readFileSync(budgetPath, "utf8"));
  for (const [route, info] of Object.entries(measured)) {
    // maxKB(현행에서 승계한 목표)는 건드리지 않는다. 갱신 대상은 회귀 기준선뿐이다.
    budgetFile.routes[route] = {
      ...budgetFile.routes[route],
      currentKB: info.totalKB,
      currentSharedKB: info.sharedKB,
      currentRouteKB: info.routeKB,
    };
  }

  const stale = Object.keys(budgetFile.routes).filter((route) => !(route in measured));
  if (stale.length > 0) {
    // 자동 삭제하지 않는다 — 아직 구현되지 않은 라우트와 사라진 라우트를 여기서
    // 구분할 수 없다. 사람이 판단하도록 알리기만 한다.
    console.warn(
      `\n  경고 예산에만 있고 측정되지 않은 라우트 ${stale.length}개: ${stale.join(", ")}\n` +
        "        아직 구현 전이면 그대로 두고, 사라진 라우트면 예산에서 지우세요.",
    );
  }

  writeFileSync(budgetPath, `${JSON.stringify(budgetFile, null, 2)}\n`);
  console.log(`회귀 기준선 갱신: ${budgetPath}`);
  process.exit(0);
}

const { routes: budget } = JSON.parse(readFileSync(budgetPath, "utf8"));
let failed = degraded.length > 0;
const overTarget = [];
const sharedGrowth = [];

console.log("\n예산 비교:");
for (const [route, limits] of Object.entries(budget)) {
  const actual = measured[route];
  if (!actual) {
    console.log(`  대기  ${route}: 아직 구현되지 않음 (목표 ${limits.maxKB} KB)`);
    continue;
  }

  const verdict = evaluateRoute(limits, actual);
  if (verdict.failed) failed = true;
  if (!verdict.withinTarget) {
    // 목표를 넘었다. 다만 지금 초과분의 대부분은 프레임워크 공용 청크에서 오고, 이 값은
    // 화면 코드로 줄일 수 있는 성질이 아니다. 목표는 앱이 완성된 뒤 §29.4의 "공개
    // 라우트가 현행 대비 증가하지 않음"으로 판정하고, 그때까지는 회귀만 막는다.
    overTarget.push(route);
  }

  if (verdict.noBaseline) {
    const suffix = verdict.failed
      ? `(목표 ${limits.maxKB} KB 초과) — --save-baseline으로 기준선을 기록하세요.`
      : `/ 목표 ${limits.maxKB} KB (기준선 없음)`;
    console.log(`  ${verdict.status}  ${route}: ${actual.totalKB} KB ${suffix}`);
    continue;
  }

  let line =
    `  ${verdict.status}  ${route}: ${actual.totalKB} KB ` +
    `(기준선 ${limits.currentKB} KB, 허용 +${verdict.allowedKB} KB, 목표 ${limits.maxKB} KB)`;
  // 회귀했을 때 공용 셸이 커진 건지 그 화면이 커진 건지가 가장 먼저 알아야 할 정보다.
  if (verdict.failed && verdict.sharedDeltaKB !== undefined) {
    line += ` — 공용 ${signed(verdict.sharedDeltaKB)}, 전용 ${signed(verdict.routeDeltaKB)}`;
    if (verdict.sharedDeltaKB > 0) sharedGrowth.push(route);
  }
  console.log(line);

  if (verdict.failed) {
    for (const chunk of actual.chunks) {
      console.log(
        `           ${String(toKB(chunkBytes(chunk))).padStart(6)} KB  ${chunk}` +
          `${sharedChunks.has(chunk) ? "  (공용)" : ""}`,
      );
    }
  }
}

for (const route of Object.keys(measured)) {
  if (!(route in budget)) {
    failed = true;
    console.error(
      `  누락  ${route}: 예산이 정의돼 있지 않습니다 — bundle-budget.json에 추가하세요.`,
    );
  }
}

if (sharedGrowth.length > 1) {
  console.log(
    `\n공용 셸 증가가 ${sharedGrowth.length}개 라우트를 동시에 밀어올렸습니다: ` +
      `${sharedGrowth.join(", ")}\n` +
      "  개별 화면이 아니라 공용 청크를 먼저 보세요 — 위 목록의 (공용) 표시가 그 대상입니다.",
  );
}

if (overTarget.length > 0) {
  console.log(
    `\n목표 미달 라우트 ${overTarget.length}개: ${overTarget.join(", ")}\n` +
      "  현재는 회귀만 막는 상태다. 앱이 완성되면 §29.4 기준으로 목표 달성 여부를 판정한다.",
  );
}

if (failed) {
  console.error(
    "\n번들 예산 초과. 예산은 Lighthouse LCP 실패에서 역산된 값이라 완화 대상이 아닙니다.",
  );
  process.exit(1);
}
console.log("\nOK: 모든 라우트가 예산 이내입니다.");
