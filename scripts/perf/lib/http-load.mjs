// B-06: 최소 의존성 부하 도구. k6/JMeter 설치가 어려운 환경(이 저장소의 CI/로컬 모두
// Node는 항상 있음)에서도 재현 가능하도록 Node 내장 fetch만 사용한다. 알고리즘 자체의
// 미세 벤치마크(JMH 대상)가 아니라 "엔드투엔드 지연·처리량·오류율"이 목적이므로 이 정도
// 단순함으로 충분하다 — 정교한 부하 분포(ramp-up 등)가 필요해지면 k6로 교체를 검토한다.
import { performance } from "node:perf_hooks";

/**
 * @param {() => Promise<{ ok: boolean }>} requestFn 요청 1회. ok=false는 실패로 집계.
 * @param {{ concurrency: number, durationMs: number }} options
 * @returns {Promise<{ latenciesMs: number[], errors: number, total: number, wallMs: number }>}
 */
export async function runLoad(requestFn, { concurrency, durationMs }) {
  const latenciesMs = [];
  let errors = 0;
  let total = 0;
  const deadline = performance.now() + durationMs;

  async function worker() {
    while (performance.now() < deadline) {
      const start = performance.now();
      let ok = true;
      try {
        const result = await requestFn();
        ok = result.ok;
      } catch {
        ok = false;
      }
      const elapsed = performance.now() - start;
      total++;
      if (ok) {
        latenciesMs.push(elapsed);
      } else {
        errors++;
      }
    }
  }

  const wallStart = performance.now();
  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  const wallMs = performance.now() - wallStart;

  return { latenciesMs, errors, total, wallMs };
}

function percentile(sortedAsc, p) {
  if (sortedAsc.length === 0) return null;
  const index = Math.min(sortedAsc.length - 1, Math.ceil((p / 100) * sortedAsc.length) - 1);
  return sortedAsc[Math.max(0, index)];
}

export function summarize({ latenciesMs, errors, total, wallMs }) {
  const sorted = [...latenciesMs].sort((a, b) => a - b);
  const successCount = sorted.length;
  return {
    total,
    successCount,
    errors,
    errorRate: total === 0 ? 0 : errors / total,
    throughputPerSec: total === 0 ? 0 : total / (wallMs / 1000),
    p50Ms: round1(percentile(sorted, 50)),
    p95Ms: round1(percentile(sorted, 95)),
    p99Ms: round1(percentile(sorted, 99)),
    maxMs: round1(sorted[sorted.length - 1] ?? null),
  };
}

function round1(value) {
  return value === null || value === undefined ? null : Math.round(value * 10) / 10;
}
