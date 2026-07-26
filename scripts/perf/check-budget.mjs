// B-06: "의도적 성능 변경 시 비교" — results.json을 budget.json과 비교해 회귀를 잡는다.
// budget.json은 `node scripts/perf/run.mjs --save-baseline`로 생성한다.
import { readFileSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const resultsPath = path.join(scriptDir, "results.json");
const budgetPath = path.join(scriptDir, "budget.json");

if (!existsSync(resultsPath) || !existsSync(budgetPath)) {
  console.error("ERROR: results.json 또는 budget.json이 없습니다. 먼저 run.mjs를 실행하세요.");
  process.exit(1);
}

const { results } = JSON.parse(readFileSync(resultsPath, "utf8"));
const budget = JSON.parse(readFileSync(budgetPath, "utf8"));

let failed = false;
for (const [name, limits] of Object.entries(budget)) {
  const actual = results[name];
  if (!actual) {
    console.warn(`SKIP ${name}: results.json에 없음(시나리오가 바뀌었나?)`);
    continue;
  }
  const p95Ok = actual.p95Ms <= limits.maxP95Ms;
  const errOk = actual.errorRate <= limits.maxErrorRate;
  const status = p95Ok && errOk ? "OK  " : "FAIL";
  if (!p95Ok || !errOk) failed = true;
  console.log(
    `${status} ${name}: p95=${actual.p95Ms}ms(예산 ${limits.maxP95Ms}ms) ` +
      `오류율=${(actual.errorRate * 100).toFixed(2)}%(예산 ${(limits.maxErrorRate * 100).toFixed(2)}%)`
  );
}

process.exit(failed ? 1 : 0);
