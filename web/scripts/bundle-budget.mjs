// 라우트별 First Load JS를 측정해 예산과 비교한다 — improvement_fe.md §19.1, §19.5.
//
// 측정 근거(레거시에서 얻은 환경 지식): Turbopack 빌드는 webpack 시절의
// "Route (app) … First Load JS" 표를 콘솔에 찍지 않는다. 대신
// `.next/server/app/**/page_client-reference-manifest.js`의
// entryJSFiles["[project]/src/app/<route>/page"]가 그 라우트가 실제로 내려받는 JS
// 청크 목록이고, 이 배열은 공용 레이아웃 청크를 이미 포함한다. 클라이언트 컴포넌트가
// 하나도 없는 순수 RSC 라우트는 자기 키가 아예 없으므로, 그 경우 레이아웃 단독 크기를
// 바닥값으로 쓴다 — 안 그러면 그 라우트가 측정에서 통째로 빠진다.
//
//   npm run build && npm run budget:bundle          측정 + 예산 비교
//   node scripts/bundle-budget.mjs --save-baseline  이번 측정치를 예산으로 저장
import { existsSync, globSync, readFileSync, statSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

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

const toKB = (bytes) => Math.round((bytes / 1024) * 10) / 10;

function readManifest(file) {
  const source = readFileSync(file, "utf8");
  const body = source.slice(source.indexOf("= {") + 2).trim();
  return JSON.parse(body.endsWith(";") ? body.slice(0, -1) : body);
}

function manifestFiles() {
  // route_…(API·robots·sitemap·아이콘·OG)는 브라우저가 렌더하는 페이지가 아니고,
  // _global-error는 Next 내부 래퍼라 둘 다 예산 대상이 아니다.
  return globSync("**/page_client-reference-manifest.js", { cwd: appServerDir })
    .filter((file) => !file.startsWith("_global-error"))
    .map((file) => path.join(appServerDir, file));
}

/** manifest 파일 경로 → Next 내부 entry 키에 쓰이는 상대 경로("(public)/page" 등). */
function entryPathOf(file) {
  return path
    .relative(appServerDir, file)
    .replaceAll("\\", "/")
    .replace(/_client-reference-manifest\.js$/, "");
}

/**
 * 라우트 이름. 라우트 그룹 세그먼트는 URL에 나타나지 않으므로 제거한다 —
 * `(public)/page`는 `/`, `(ops)/ops/page`는 `/ops`다. 예산 키는 URL 기준이라
 * 이걸 안 벗기면 실제 라우트와 매칭되지 않는다.
 */
function routeOf(file) {
  const segments = entryPathOf(file)
    .split("/")
    .filter((segment) => segment !== "" && segment !== "page" && !/^\(.+\)$/.test(segment));
  return `/${segments.join("/")}`;
}

/**
 * 이 manifest가 나타내는 페이지 자신의 entry 키를 고른다. 경로를 문자열로 조립해
 * 맞히려 들면 Next가 키를 정규화하는 방식(라우트 그룹·병렬 라우트)에 따라 조용히
 * 빗나가고, 그러면 모든 라우트가 레이아웃 바닥값으로 측정돼 예산이 무의미해진다.
 */
function ownEntryKey(file, entries) {
  const rel = entryPathOf(file);
  const exact = Object.keys(entries).find((key) => key === `[project]/src/app/${rel}`);
  if (exact) return exact;

  const pageKeys = Object.keys(entries).filter((key) => key.endsWith("/page"));
  return pageKeys.length === 1 ? pageKeys[0] : null;
}

function chunkBytes(chunks) {
  return [...new Set(chunks)].reduce((sum, chunk) => {
    const file = path.join(distDir, chunk);
    return sum + (existsSync(file) ? statSync(file).size : 0);
  }, 0);
}

const files = manifestFiles();

/**
 * 이 라우트가 클라이언트 컴포넌트를 하나도 안 가져도 받는 JS — 자기 레이아웃 체인이다.
 * 전역에서 하나만 구해 공유하면 셸이 갈라진 구조(공개/세션/운영)에서 틀린 값이 된다.
 */
function layoutChunksOf(entries) {
  const layoutKeys = Object.keys(entries).filter((key) => key.endsWith("/layout"));
  return layoutKeys.flatMap((key) => entries[key] ?? []);
}

const measured = {};
for (const file of files) {
  const entries = readManifest(file).entryJSFiles ?? {};
  const ownKey = ownEntryKey(file, entries);
  const chunks = ownKey ? (entries[ownKey] ?? []) : [];

  if (!ownKey && Object.keys(entries).length > 0) {
    // 키를 못 찾았다는 것은 측정이 바닥값으로 퇴화했다는 뜻이다 — 조용히 넘기면
    // "예산 통과"가 거짓말이 된다. 실제 키를 찍어 다음 수정에 쓴다.
    console.warn(
      `  경고 ${routeOf(file)}: 자기 entry 키를 찾지 못해 바닥값으로 측정합니다. ` +
        `manifest 키: ${Object.keys(entries).join(", ")}`,
    );
  }

  const layoutChunks = layoutChunksOf(entries);
  const effective = chunks.length > 0 ? chunks : layoutChunks;
  const unique = [...new Set(effective)];

  measured[routeOf(file)] = {
    totalKB: toKB(chunkBytes(unique)),
    chunkCount: unique.length,
    chunks: unique,
  };
}

console.log("라우트별 초기 클라이언트 JS (내림차순):");
for (const [route, info] of Object.entries(measured).sort((a, b) => b[1].totalKB - a[1].totalKB)) {
  console.log(`  ${String(info.totalKB).padStart(7)} KB  (청크 ${info.chunkCount}개)  ${route}`);
  // 어떤 청크가 무게를 차지하는지 없이는 "예산 초과"를 고칠 방법을 알 수 없다.
  for (const chunk of info.chunks) {
    console.log(`           ${String(toKB(chunkBytes([chunk]))).padStart(6)} KB  ${chunk}`);
  }
}

writeFileSync(
  resultsPath,
  `${JSON.stringify({ measuredAt: new Date().toISOString(), routes: measured }, null, 2)}\n`,
);
console.log(`\n결과 저장: ${resultsPath}`);

/**
 * 회귀 허용치. 측정은 빌드마다 미세하게 흔들리므로 1KB까지는 같은 값으로 본다 —
 * 이보다 좁히면 코드와 무관한 실패가 생겨 게이트를 신뢰하지 않게 된다.
 */
const REGRESSION_TOLERANCE_KB = 1;

if (saveBaseline) {
  const budget = JSON.parse(readFileSync(budgetPath, "utf8"));
  for (const [route, info] of Object.entries(measured)) {
    // maxKB(현행에서 승계한 목표)는 건드리지 않는다. 갱신 대상은 회귀 기준선뿐이다.
    budget.routes[route] = { ...budget.routes[route], currentKB: info.totalKB };
  }
  writeFileSync(budgetPath, `${JSON.stringify(budget, null, 2)}\n`);
  console.log(`회귀 기준선 갱신: ${budgetPath}`);
  process.exit(0);
}

const { routes: budget } = JSON.parse(readFileSync(budgetPath, "utf8"));
let failed = false;
const overTarget = [];

console.log("\n예산 비교:");
for (const [route, { maxKB, currentKB }] of Object.entries(budget)) {
  const actual = measured[route];
  if (!actual) {
    console.log(`  대기  ${route}: 아직 구현되지 않음 (목표 ${maxKB} KB)`);
    continue;
  }

  if (actual.totalKB <= maxKB) {
    console.log(`  통과  ${route}: ${actual.totalKB} KB / 목표 ${maxKB} KB`);
    continue;
  }

  // 목표를 넘었다. 다만 지금 초과분의 대부분은 프레임워크 공용 청크(53KB)에서 오고,
  // 이 값은 화면 코드로 줄일 수 있는 성질이 아니다. 목표는 앱이 완성된 뒤 §29.4의
  // "공개 라우트가 현행 대비 증가하지 않음"으로 판정하고, 그때까지는 **여기서 더
  // 늘어나는 것**을 막는다. 기준선이 없는 라우트는 이번 값이 기준선이 된다.
  overTarget.push(route);

  if (currentKB === undefined) {
    console.log(
      `  기준선 없음 ${route}: ${actual.totalKB} KB (목표 ${maxKB} KB 초과) — ` +
        "--save-baseline으로 기준선을 기록하세요.",
    );
    failed = true;
    continue;
  }

  const grew = actual.totalKB > currentKB + REGRESSION_TOLERANCE_KB;
  if (grew) failed = true;
  console.log(
    `  ${grew ? "회귀" : "유지"}  ${route}: ${actual.totalKB} KB ` +
      `(기준선 ${currentKB} KB, 목표 ${maxKB} KB)`,
  );
}

for (const route of Object.keys(measured)) {
  if (!(route in budget)) {
    failed = true;
    console.error(
      `  누락  ${route}: 예산이 정의돼 있지 않습니다 — bundle-budget.json에 추가하세요.`,
    );
  }
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
