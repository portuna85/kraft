// B-06: 재현 가능한 성능 베이스라인 실행기.
//
// 가정 공개(완료 조건 요구사항):
//   - 런타임: Java 25, Spring Boot 4.1, virtual threads enabled
//   - DB: 이 스크립트를 그대로 실행하면 local 프로파일 H2 인메모리를 사용한다.
//     운영은 MariaDB 11.7 + Hikari maximum-pool-size=15다. H2 숫자는 MariaDB 성능을
//     대표하지 않는다 — Docker로 MariaDB를 띄우고 KRAFT_DB_URL 등을 맞춰 같은
//     스크립트를 재실행하면 운영에 더 가까운 숫자를 얻는다(이 세션 환경엔 Docker
//     데몬이 없어 H2로만 실행했다).
//   - 하드웨어: 이 스크립트를 실행한 머신의 CPU/메모리에 의존한다 — 절대 수치가
//     아니라 "회귀 여부"를 보려면 매번 같은 머신에서 baseline과 비교해야 한다.
//
// 사용:
//   1) 백엔드를 로컬(H2)로 띄운다: KRAFT_OPS_TOKEN=test-token ./gradlew.bat bootRun
//   2) (최초 1회) 데이터 시딩: KRAFT_OPS_TOKEN=test-token node scripts/perf/seed-rounds.mjs 300
//   3) node scripts/perf/run.mjs
//   4) 결과를 기준선으로 삼으려면: node scripts/perf/run.mjs --save-baseline
//   5) 이후 회귀 확인: node scripts/perf/check-budget.mjs
import { writeFileSync, readFileSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import os from "node:os";
import { runLoad, summarize } from "./lib/http-load.mjs";
import { buildScenarios } from "./scenarios.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const baseUrl = process.env.PERF_BASE_URL ?? "http://localhost:8080";
const saveBaseline = process.argv.includes("--save-baseline");

const scenarios = buildScenarios(baseUrl);
const results = {};

console.log(`B-06 성능 베이스라인 실행 — ${baseUrl}`);
console.log(`머신: ${os.cpus().length}코어 ${os.cpus()[0]?.model ?? "unknown"}, ${Math.round(os.totalmem() / 1e9)}GB\n`);

for (const scenario of scenarios) {
  process.stdout.write(`${scenario.name} (동시성 ${scenario.concurrency}, ${scenario.durationMs}ms) ... `);
  const raw = await runLoad(scenario.request, {
    concurrency: scenario.concurrency,
    durationMs: scenario.durationMs,
  });
  const summary = summarize(raw);
  results[scenario.name] = summary;
  console.log(
    `p50=${summary.p50Ms}ms p95=${summary.p95Ms}ms p99=${summary.p99Ms}ms ` +
      `처리량=${summary.throughputPerSec.toFixed(1)}/s 오류율=${(summary.errorRate * 100).toFixed(2)}%`
  );
}

const outPath = path.join(scriptDir, "results.json");
writeFileSync(outPath, JSON.stringify({ timestamp: new Date().toISOString(), baseUrl, results }, null, 2));
console.log(`\n결과 저장: ${outPath}`);

if (saveBaseline) {
  const budgetPath = path.join(scriptDir, "budget.json");
  const budget = {};
  for (const [name, summary] of Object.entries(results)) {
    // 회귀 예산: 이번 실행의 p95/오류율에 여유(p95 +50%, 오류율 +2%p)를 둔다.
    // "이번 실행과 완전히 동일해야 통과"가 아니라 "눈에 띄게 나빠지면 잡는다"가 목적이다.
    budget[name] = {
      maxP95Ms: Math.ceil(summary.p95Ms * 1.5),
      maxErrorRate: Math.min(1, summary.errorRate + 0.02),
    };
  }
  writeFileSync(budgetPath, JSON.stringify(budget, null, 2));
  console.log(`기준선 저장: ${budgetPath}`);
}
