// Playwright JSON 결과(playwright-results.json)에서 flaky(재시도 끝에 통과한) 테스트를 찾아 GitHub 실행 요약에
// 적고 경고를 남긴다(OPS-34). retries: 1이 불안정한 테스트를 조용히 통과시키지 않게 하려는 것이다.
// 사용: node scripts/report-flaky.mjs <샤드 번호> [결과 파일]
import { appendFileSync, readFileSync } from 'node:fs';

const shard = process.argv[2] ?? '?';
const file = process.argv[3] ?? 'playwright-results.json';
const report = JSON.parse(readFileSync(file, 'utf8'));

const flaky = [];
const walk = (suite) => {
    for (const spec of suite.specs ?? []) {
        if ((spec.tests ?? []).some((test) => test.status === 'flaky')) {
            flaky.push(spec.title);
        }
    }
    (suite.suites ?? []).forEach(walk);
};
(report.suites ?? []).forEach(walk);

const lines = [`### E2E shard ${shard}: flaky ${flaky.length}`, ...flaky.map((title) => `- ${title}`)];
if (process.env.GITHUB_STEP_SUMMARY) {
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${lines.join('\n')}\n`);
} else {
    console.log(lines.join('\n'));
}
if (flaky.length > 0) {
    console.log(`::warning::E2E shard ${shard}에서 flaky 테스트 ${flaky.length}건 (요약 참고)`);
}
