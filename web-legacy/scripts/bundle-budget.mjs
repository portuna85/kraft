// F-06: 라우트별 초기 클라이언트 JS 크기를 캡처하고 예산과 비교한다.
//
// Turbopack 빌드(Next 16)는 webpack 시절의 "Route (app) ... First Load JS" 표를
// 더 이상 콘솔에 찍지 않는다. 대신 `.next/server/app/**/page_client-reference-manifest.js`
// 의 `entryJSFiles["[project]/src/app/<route>/page"]`가 그 라우트가 실제로 받는
// JS 청크 목록이다 — 이걸 파일 크기로 환산해 라우트별 합계를 구한다.
//
// 사용:
//   npm run build && node scripts/bundle-budget.mjs           # 측정만
//   node scripts/bundle-budget.mjs --save-baseline            # 이번 결과를 예산으로 저장
import { readFileSync, writeFileSync, existsSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { globSync } from "node:fs";

const webDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const nextDir = path.join(webDir, ".next");
const appServerDir = path.join(nextDir, "server", "app");
const resultsPath = path.join(webDir, "scripts", "bundle-results.json");
const budgetPath = path.join(webDir, "scripts", "bundle-budget.json");
const saveBaseline = process.argv.includes("--save-baseline");

if (!existsSync(appServerDir)) {
  console.error(`ERROR: ${appServerDir} 없음 — 먼저 "npm run build"를 실행하세요.`);
  process.exit(1);
}

function parseManifest(filePath) {
  const content = readFileSync(filePath, "utf8");
  const idx = content.indexOf("= {");
  let jsonStr = content.slice(idx + 2).trim();
  if (jsonStr.endsWith(";")) jsonStr = jsonStr.slice(0, -1);
  return JSON.parse(jsonStr);
}

// [project]/src/app/analysis/page 형태의 키에서, 이 manifest 파일이 속한 라우트 자신의
// entryJSFiles 키만 골라낸다(레이아웃·not-found·error 등 형제 항목은 제외).
function ownEntryKey(manifestFile, entryJSFiles) {
  const rel = path
    .relative(appServerDir, manifestFile)
    .replace(/\\/g, "/")
    .replace(/_client-reference-manifest\.js$/, "");
  // rel 예: "analysis/page", "community/posts/[id]/page", "api/revalidate/route"
  const wanted = `[project]/src/app/${rel}`;
  return entryJSFiles[wanted] ? wanted : null;
}

// route_client-reference-manifest.js(API 라우트, robots.txt, sitemap.xml, 아이콘/OG
// 이미지 등)는 브라우저가 렌더하는 "페이지"가 아니라 번들 예산과 무관하므로 제외한다.
// _global-error는 Next 내부 최상위 에러 래퍼로 사용자가 직접 방문하는 라우트가 아니라
// 역시 제외한다(실제 라우트 단위 error.tsx 경계는 각 세그먼트의 page 항목에 반영됨).
function findManifests() {
  return globSync("**/page_client-reference-manifest.js", { cwd: appServerDir })
    .filter((f) => !f.startsWith("_global-error"))
    .map((f) => path.join(appServerDir, f));
}

function chunkSizeBytes(chunkRelPath) {
  const filePath = path.join(nextDir, chunkRelPath);
  if (!existsSync(filePath)) return 0;
  return statSync(filePath).size;
}

function routeNameFromManifestPath(manifestFile) {
  const rel = path
    .relative(appServerDir, manifestFile)
    .replace(/\\/g, "/")
    .replace(/(^|\/)page_client-reference-manifest\.js$/, "")
    .replace(/(^|\/)route_client-reference-manifest\.js$/, "");
  return "/" + rel;
}

// 페이지 자신의 entryJSFiles 배열은 이미 공용 레이아웃 청크를 포함한다(직접 확인함) —
// 그래서 route-own 항목이 있으면 그게 곧 "이 라우트의 First Load JS 총량"이다.
// 문제는 상호작용 컴포넌트가 전혀 없는 순수 서버 컴포넌트 라우트(/stats, /status 등)는
// entryJSFiles에 자기 키 자체가 없다는 것 — 그래도 레이아웃 JS는 여전히 받으므로,
// 그 경우엔 레이아웃 단독 크기를 "바닥값"으로 사용해야 라우트를 빠뜨리지 않는다.
function findLayoutBytes() {
  for (const manifestFile of findManifests()) {
    const manifest = parseManifest(manifestFile);
    const layoutChunks = manifest.entryJSFiles?.["[project]/src/app/layout"];
    if (layoutChunks) {
      return [...new Set(layoutChunks)].reduce((sum, c) => sum + chunkSizeBytes(c), 0);
    }
  }
  return 0;
}

const sharedLayoutBytes = findLayoutBytes();
const routes = {};
for (const manifestFile of findManifests()) {
  const manifest = parseManifest(manifestFile);
  const entryJSFiles = manifest.entryJSFiles ?? {};
  const key = ownEntryKey(manifestFile, entryJSFiles);
  const chunks = key ? (entryJSFiles[key] ?? []) : [];
  const routeName = routeNameFromManifestPath(manifestFile);

  if (chunks.length === 0) {
    // 이 라우트만의 클라이언트 컴포넌트가 없다 — 그래도 레이아웃 JS는 받는다.
    routes[routeName] = {
      totalKB: Math.round((sharedLayoutBytes / 1024) * 10) / 10,
      chunkCount: sharedLayoutBytes > 0 ? 1 : 0,
    };
    continue;
  }

  const uniqueChunks = [...new Set(chunks)];
  const totalBytes = uniqueChunks.reduce((sum, c) => sum + chunkSizeBytes(c), 0);
  routes[routeName] = { totalKB: Math.round((totalBytes / 1024) * 10) / 10, chunkCount: uniqueChunks.length };
}

const sorted = Object.entries(routes).sort((a, b) => b[1].totalKB - a[1].totalKB);

console.log(`공용 레이아웃(모든 라우트 바닥값): ${Math.round((sharedLayoutBytes / 1024) * 10) / 10} KB\n`);
console.log("라우트별 초기 클라이언트 JS (내림차순):");
for (const [route, info] of sorted) {
  console.log(`  ${info.totalKB.toString().padStart(7)} KB  (${info.chunkCount}개 청크)  ${route}`);
}

writeFileSync(resultsPath, JSON.stringify({ timestamp: new Date().toISOString(), routes }, null, 2));
console.log(`\n결과 저장: ${resultsPath}`);

if (saveBaseline) {
  const budget = {};
  for (const [route, info] of Object.entries(routes)) {
    // 회귀 예산: 이번 측정치 + 25%. "지금과 완전히 동일해야 통과"가 아니라
    // "눈에 띄게 커지면 잡는다"가 목적이다(B-06과 동일한 철학).
    budget[route] = { maxKB: Math.ceil(info.totalKB * 1.25) };
  }
  writeFileSync(budgetPath, JSON.stringify(budget, null, 2));
  console.log(`기준선 저장: ${budgetPath}`);
} else if (existsSync(budgetPath)) {
  const budget = JSON.parse(readFileSync(budgetPath, "utf8"));
  let failed = false;
  console.log("\n예산 비교:");
  for (const [route, limit] of Object.entries(budget)) {
    const actual = routes[route];
    if (!actual) {
      console.warn(`  SKIP ${route}: 이번 빌드에 없음(라우트가 제거/이름 변경됐나?)`);
      continue;
    }
    const ok = actual.totalKB <= limit.maxKB;
    if (!ok) failed = true;
    console.log(`  ${ok ? "OK  " : "FAIL"} ${route}: ${actual.totalKB}KB (예산 ${limit.maxKB}KB)`);
  }
  if (failed) {
    console.error("\n번들 예산 초과 — bundle-budget.json 대비 커진 라우트가 있습니다.");
    process.exit(1);
  }
}
