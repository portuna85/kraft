// F-06: "Lighthouse는 실험실 진단 용도로만" — 실사용자 데이터(web-vitals-reporter.tsx →
// /api/vitals)를 대신하지 않는다. 이 스크립트는 배포 전 로컬/CI에서 회귀를 조기에
// 잡는 랩 진단이고, 운영 판단의 근거는 실제 필드 데이터(RUM)여야 한다.
//
// 사용 (앱이 이미 떠 있어야 함, 예: npm run build && npm run e2e:serve):
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

// Core Web Vitals "good" 기준(완료 조건에 명시된 값). 이 값들과의 비교는 랩 측정치가
// 참고 삼아 재는 것이지, "이 페이지의 실제 CWV 등급"이라고 주장하는 게 아니다 —
// 실제 등급은 /api/vitals로 모은 필드 p75로만 매길 수 있다.
const CWV_GOOD = { lcpMs: 2500, clsScore: 0.1 };

const ROUTES = ["/", "/recommend", "/frequency"];

async function runLighthouseFor(route, chrome) {
  const result = await lighthouse(`${baseUrl}${route}`, {
    port: chrome.port,
    output: "json",
    onlyCategories: ["performance"],
    formFactor: "mobile",
    screenEmulation: { mobile: true, width: 412, height: 823, deviceScaleFactor: 2.625 },
    // Lighthouse의 기본 "simulated" 4G 스로틀링 모델을 localhost에 그대로 적용하면
    // LCP가 15초 넘게 나오는 등 완전히 비현실적인 값이 나오는 것을 실제로 확인했다
    // (스로틀링을 끄자 같은 페이지가 280ms로 나옴) — 시뮬레이션이 구글의 실측 보정
    // 환경 밖에서는 신뢰할 수 없다는 알려진 한계다. "제공된(provided)" 방식, 즉
    // 이 머신·네트워크의 실측 조건 그대로 재도록 한다. 결과가 "느린 4G에서 어떤가"가
    // 아니라 "이 코드가 실제로 얼마나 무거운가"를 보려는 것이므로 이게 맞는 선택이다.
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
        `CLS=${summary.clsScore}(${clsFlag}) TBT=${summary.tbtMs}ms`
    );
  }
} finally {
  await chrome.kill();
}

writeFileSync(resultsPath, JSON.stringify({ timestamp: new Date().toISOString(), baseUrl, results }, null, 2));
console.log(`\n결과 저장: ${resultsPath}`);

if (saveBaseline) {
  const budget = {};
  for (const [route, summary] of Object.entries(results)) {
    // 예산: 이번 측정치에 여유(LCP/TBT +50%, CLS +0.05, 점수 -10점)를 둔다 — 다른
    // 두 예산 스크립트(perf/run.mjs, bundle-budget.mjs)와 동일한 철학.
    budget[route] = {
      maxLcpMs: Math.ceil(summary.lcpMs * 1.5),
      maxClsScore: Math.round((summary.clsScore + 0.05) * 1000) / 1000,
      maxTbtMs: Math.ceil(summary.tbtMs * 1.5),
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
    const checks = [
      ["LCP", actual.lcpMs <= limit.maxLcpMs, `${actual.lcpMs}ms/${limit.maxLcpMs}ms`],
      ["CLS", actual.clsScore <= limit.maxClsScore, `${actual.clsScore}/${limit.maxClsScore}`],
      ["TBT", actual.tbtMs <= limit.maxTbtMs, `${actual.tbtMs}ms/${limit.maxTbtMs}ms`],
      ["Score", actual.performanceScore >= limit.minPerformanceScore, `${actual.performanceScore}/${limit.minPerformanceScore}`],
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
