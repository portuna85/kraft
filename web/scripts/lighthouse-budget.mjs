// web-legacy/scripts/lighthouse-budget.mjs를 이 앱 전용으로 들여왔다(레거시는 동결이라
// 그대로 두고 손대지 않는다) P-5·P-11 검증용.
//
// "Lighthouse는 실험실 진단 용도로만" — 실사용자 데이터(RUM, /api/vitals)를 대신하지
// 않는다. 이 스크립트는 배포 전 로컬/CI에서 회귀를 조기에 잡는 랩 진단이고, 운영
// 판단의 근거는 실제 필드 데이터여야 한다.
//
// KF-05(docs/improvement.md): LCP는 게이트가 아니라 참고 정보다 — throttlingMethod
// "provided"(무스로틀 localhost) 측정이라 실측 72~182ms에 예산 200~280ms를 걸어도
// 실제 회귀를 잡을 수 없다. CLS/TBT/Score만 실패(exit 1)를 만든다.
//
// 사용 (앱이 이미 떠 있어야 함, 예: npm run build && npm start):
//   node scripts/lighthouse-budget.mjs
//   node scripts/lighthouse-budget.mjs --save-baseline
import { writeFileSync, readFileSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import * as chromeLauncher from "chrome-launcher";
import lighthouse from "lighthouse";

const webDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const resultsPath = path.join(webDir, "scripts", "lighthouse-results.json");
const budgetPath = path.join(webDir, "scripts", "lighthouse-budget.json");
const baseUrl = process.env.PERF_BASE_URL ?? "http://localhost:3000";
const saveBaseline = process.argv.includes("--save-baseline");

// Core Web Vitals "good" 기준(참고용). 이 값들과의 비교는 랩 측정치를 참고 삼아 재는
// 것이지 "이 페이지의 실제 CWV 등급"이라고 주장하는 게 아니다 — 실제 등급은
// /api/vitals로 모은 필드 p75로만 매길 수 있다.
const CWV_GOOD = { lcpMs: 2500, clsScore: 0.1 };

// bundle-budget.json과 맞춘 대표 라우트. /community/posts/[id]는 실제 게시글이
// 필요해 픽스처 백엔드 없이는 의미 있는 값을 얻을 수 없으므로, 백엔드가 붙어 있고
// 그 글 id를 알 때만(PERF_POST_ID) 포함한다.
const POST_ID = process.env.PERF_POST_ID;
const ROUTES = [
  "/",
  "/recommend",
  "/frequency",
  "/community",
  "/saved",
  // KF-05(docs/improvement.md): 실측 CLS 0.116인 실패 라우트인데 예산에 없었다.
  "/recommend/history",
  ...(POST_ID ? [`/community/posts/${POST_ID}`] : []),
];

// KF-05(docs/improvement.md)이 아직 안 고쳐진 라우트 — 이 CLS는 알려진 결함
// (KF-08, 세션 게이트 워터폴)이라 CWV_GOOD 상한을 걸면 매번 실패한다. KF-08 수정
// (§10 4~5단계) 후 이 목록에서 빼고 --save-baseline을 다시 실행해 진짜 상한을 건다.
const PENDING_CLS_ROOT_CAUSE = new Set(["/saved", "/recommend/history"]);

// 느린 CI 머신에서 tbtMs 0ms 기준선의 ceil(0*1.5)=0 예산이 하드 실패하지 않도록
// 하는 바닥값. CWV "good" TBT 기준(200ms)의 절반 — 실제 회귀엔 여전히 엄격하다.
const MIN_TBT_BUDGET_MS = 100;

async function runLighthouseFor(route, chrome) {
  const result = await lighthouse(`${baseUrl}${route}`, {
    port: chrome.port,
    output: "json",
    onlyCategories: ["performance"],
    formFactor: "mobile",
    screenEmulation: { mobile: true, width: 412, height: 823, deviceScaleFactor: 2.625 },
    // Lighthouse의 기본 "simulated" 4G 스로틀링 모델을 localhost에 그대로 적용하면
    // LCP가 비현실적으로 크게 나온다(레거시에서 실측 확인됨 — 시뮬레이션이 구글의
    // 실측 보정 환경 밖에서는 신뢰할 수 없다는 알려진 한계). "제공된(provided)" 방식,
    // 즉 이 머신·네트워크의 실측 조건 그대로 재도록 한다.
    throttlingMethod: "provided",
  });
  const audits = result.lhr.audits;
  return {
    performanceScore: Math.round((result.lhr.categories.performance.score ?? 0) * 100),
    lcpMs: Math.round(audits["largest-contentful-paint"].numericValue),
    clsScore: Math.round(audits["cumulative-layout-shift"].numericValue * 1000) / 1000,
    tbtMs: Math.round(audits["total-blocking-time"].numericValue),
  };
}

const chromeFlags = ["--headless=new"];
if (typeof process.getuid === "function" && process.getuid() === 0) {
  chromeFlags.push("--no-sandbox");
}
const chrome = await chromeLauncher.launch({ chromeFlags });
const results = {};
try {
  for (const route of ROUTES) {
    process.stdout.write(`${route} ... `);
    const summary = await runLighthouseFor(route, chrome);
    results[route] = summary;
    const lcpFlag = summary.lcpMs <= CWV_GOOD.lcpMs ? "OK" : "위험";
    const clsFlag = summary.clsScore <= CWV_GOOD.clsScore ? "OK" : "위험";
    console.log(
      `score=${summary.performanceScore} LCP=${summary.lcpMs}ms(${lcpFlag}) ` +
        `CLS=${summary.clsScore}(${clsFlag}) TBT=${summary.tbtMs}ms`,
    );
  }
} finally {
  try {
    await chrome.kill();
  } catch (error) {
    // chrome-launcher가 Chrome 종료 후 임시 프로필을 지우다 실패하는 경우가 있다
    // (Windows에서 시스템 프로세스가 파일을 잡고 있을 때). 측정 결과는 살리고
    // 정리 실패만 알린다.
    if (process.platform !== "win32" || error?.code !== "EPERM") {
      throw error;
    }
    console.warn(
      "Chrome 임시 프로필을 정리하지 못했습니다(Windows EPERM). 성능 예산 검증은 계속합니다.",
    );
  }
}

writeFileSync(
  resultsPath,
  JSON.stringify({ timestamp: new Date().toISOString(), baseUrl, results }, null, 2),
);
console.log(`\n결과 저장: ${resultsPath}`);

if (saveBaseline) {
  const budget = {};
  for (const [route, summary] of Object.entries(results)) {
    // 예산: 이번 측정치에 여유(LCP/TBT +50%, CLS +0.05, 점수 -10점)를 둔다 — 다른
    // 예산 스크립트(bundle-budget.mjs)와 같은 철학.
    //
    // KF-05(docs/improvement.md): 이 여유 공식이 그대로면 CWV "good" 기준(0.1)을
    // 이미 넘긴 실패 상태(예: /saved 0.123)도 통째로 예산으로 승격돼 "게이트가
    // 실패를 정상으로 승인"하는 꼴이 된다. PENDING_CLS_ROOT_CAUSE에 없는 라우트는
    // CWV_GOOD.clsScore를 상한으로 건다.
    const rawClsBudget = Math.round((summary.clsScore + 0.05) * 1000) / 1000;
    const maxClsScore = PENDING_CLS_ROOT_CAUSE.has(route)
      ? rawClsBudget
      : Math.min(rawClsBudget, CWV_GOOD.clsScore);
    budget[route] = {
      maxLcpMs: Math.ceil(summary.lcpMs * 1.5),
      maxClsScore,
      maxTbtMs: Math.max(MIN_TBT_BUDGET_MS, Math.ceil(summary.tbtMs * 1.5)),
      minPerformanceScore: Math.max(0, summary.performanceScore - 10),
    };
  }
  writeFileSync(budgetPath, JSON.stringify(budget, null, 2));
  console.log(`기준선 저장: ${budgetPath}`);
} else if (existsSync(budgetPath)) {
  const budget = JSON.parse(readFileSync(budgetPath, "utf8"));
  let failed = false;
  console.log("\n예산 비교:");
  for (const [route, limit] of Object.entries(budget)) {
    const actual = results[route];
    if (!actual) {
      console.warn(`  SKIP ${route}: 이번 실행에 없음`);
      continue;
    }
    // KF-05: LCP는 게이트가 아니라 참고 정보다(파일 상단 주석 참고) — 실패로
    // 집계하지 않는다.
    const lcpOk = actual.lcpMs <= limit.maxLcpMs;
    console.log(
      `  INFO ${route} LCP: ${actual.lcpMs}ms/${limit.maxLcpMs}ms` +
        `${lcpOk ? "" : " (참고용, 게이트 아님)"}`,
    );

    const checks = [
      ["CLS", actual.clsScore <= limit.maxClsScore, `${actual.clsScore}/${limit.maxClsScore}`],
      ["TBT", actual.tbtMs <= limit.maxTbtMs, `${actual.tbtMs}ms/${limit.maxTbtMs}ms`],
      [
        "Score",
        actual.performanceScore >= limit.minPerformanceScore,
        `${actual.performanceScore}/${limit.minPerformanceScore}`,
      ],
    ];
    for (const [label, ok, detail] of checks) {
      if (!ok) failed = true;
      console.log(`  ${ok ? "OK  " : "FAIL"} ${route} ${label}: ${detail}`);
    }
  }
  if (failed) {
    console.error("\nLighthouse 예산 초과.");
    process.exit(1);
  }
}
