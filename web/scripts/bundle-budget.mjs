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

function routeOf(file) {
  const rel = path
    .relative(appServerDir, file)
    .replaceAll("\\", "/")
    .replace(/(^|\/)page_client-reference-manifest\.js$/, "");
  return `/${rel}`;
}

function chunkBytes(chunks) {
  return [...new Set(chunks)].reduce((sum, chunk) => {
    const file = path.join(distDir, chunk);
    return sum + (existsSync(file) ? statSync(file).size : 0);
  }, 0);
}

const files = manifestFiles();

const layoutBytes = (() => {
  for (const file of files) {
    const chunks = readManifest(file).entryJSFiles?.["[project]/src/app/layout"];
    if (chunks) return chunkBytes(chunks);
  }
  return 0;
})();

const measured = {};
for (const file of files) {
  const entries = readManifest(file).entryJSFiles ?? {};
  const ownKey = `[project]/src/app${routeOf(file) === "/" ? "" : routeOf(file)}/page`;
  const chunks = entries[ownKey] ?? [];
  measured[routeOf(file)] =
    chunks.length > 0
      ? { totalKB: toKB(chunkBytes(chunks)), chunkCount: new Set(chunks).size }
      : { totalKB: toKB(layoutBytes), chunkCount: layoutBytes > 0 ? 1 : 0 };
}

console.log(`공용 레이아웃(모든 라우트 바닥값): ${toKB(layoutBytes)} KB\n`);
console.log("라우트별 초기 클라이언트 JS (내림차순):");
for (const [route, info] of Object.entries(measured).sort((a, b) => b[1].totalKB - a[1].totalKB)) {
  console.log(`  ${String(info.totalKB).padStart(7)} KB  (청크 ${info.chunkCount}개)  ${route}`);
}

writeFileSync(
  resultsPath,
  `${JSON.stringify({ measuredAt: new Date().toISOString(), routes: measured }, null, 2)}\n`,
);
console.log(`\n결과 저장: ${resultsPath}`);

if (saveBaseline) {
  const budget = JSON.parse(readFileSync(budgetPath, "utf8"));
  for (const [route, info] of Object.entries(measured)) {
    budget.routes[route] = { maxKB: Math.ceil(info.totalKB) };
  }
  writeFileSync(budgetPath, `${JSON.stringify(budget, null, 2)}\n`);
  console.log(`예산 갱신: ${budgetPath}`);
  process.exit(0);
}

const { routes: budget } = JSON.parse(readFileSync(budgetPath, "utf8"));
let failed = false;

console.log("\n예산 비교:");
for (const [route, { maxKB }] of Object.entries(budget)) {
  const actual = measured[route];
  if (!actual) {
    // 재작성 중에는 아직 안 만든 라우트가 정상이다. 라우트가 생기는 순간 게이트가 된다.
    console.log(`  대기  ${route}: 아직 구현되지 않음 (예산 ${maxKB} KB)`);
    continue;
  }
  const status = actual.totalKB <= maxKB ? "통과" : "초과";
  if (actual.totalKB > maxKB) failed = true;
  console.log(`  ${status}  ${route}: ${actual.totalKB} KB / ${maxKB} KB`);
}

for (const route of Object.keys(measured)) {
  if (!(route in budget)) {
    failed = true;
    console.error(
      `  누락  ${route}: 예산이 정의돼 있지 않습니다 — bundle-budget.json에 추가하세요.`,
    );
  }
}

if (failed) {
  console.error(
    "\n번들 예산 초과. 예산은 Lighthouse LCP 실패에서 역산된 값이라 완화 대상이 아닙니다.",
  );
  process.exit(1);
}
console.log("\nOK: 모든 라우트가 예산 이내입니다.");
