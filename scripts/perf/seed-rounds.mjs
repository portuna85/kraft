// B-06: 통계·추천 API가 트리비얼하지 않은 응답을 내도록 회차를 시딩한다. 추천 서비스의
// 이력 완전성 게이트(P1-05)가 1회부터 최신 회차까지 빈틈을 요구하므로 반드시 1..N
// 연속으로 넣는다. /ops/rounds(수동 등록)를 순차 호출한다 — 외부 네트워크 접근 없이
// 로컬 백엔드만으로 재현 가능하게 하기 위함이다.
//
// 사용: KRAFT_OPS_TOKEN=<application.yml의 kraft.ops.token과 동일한 값> \
//       node scripts/perf/seed-rounds.mjs [count] [baseUrl]
const count = Number(process.argv[2] ?? 300);
const baseUrl = process.argv[3] ?? "http://localhost:8080";
const opsToken = process.env.KRAFT_OPS_TOKEN;

if (!opsToken) {
  console.error("ERROR: KRAFT_OPS_TOKEN 환경변수가 필요합니다(백엔드 kraft.ops.token과 동일한 값).");
  process.exit(1);
}

function numbersForRound(round) {
  const numbers = new Set();
  let seed = round;
  while (numbers.size < 7) {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    numbers.add(1 + (seed % 45));
  }
  const all = [...numbers];
  return { main: all.slice(0, 6).sort((a, b) => a - b), bonus: all[6] };
}

async function upsertRound(round) {
  const drawDate = new Date(2015, 0, 3 + round * 7).toISOString().slice(0, 10);
  const { main, bonus } = numbersForRound(round);
  const body = {
    round,
    drawDate,
    numbers: main,
    bonusNumber: bonus,
    firstPrizeAmount: 2_000_000_000,
    secondPrize: 50_000_000,
    secondWinners: 10,
    totalSales: 90_000_000_000,
    firstAccumAmount: 2_000_000_000,
  };

  // /ops/**도 공개 API와 동일한 rate limiter(kraft.security.rate-limit-per-minute,
  // 기본 120/분)를 통과한다. 429를 받으면 지수 백오프로 재시도한다.
  for (let attempt = 0; ; attempt++) {
    const res = await fetch(`${baseUrl}/ops/rounds`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Ops-Token": opsToken },
      body: JSON.stringify(body),
    });
    if (res.ok) return;
    if (res.status === 429 && attempt < 5) {
      await sleep(2000 * (attempt + 1));
      continue;
    }
    throw new Error(`round ${round} 시딩 실패: ${res.status} ${await res.text()}`);
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

console.log(`시딩 시작: 1..${count}회차 → ${baseUrl}`);
const start = Date.now();
// rate limiter(기본 120/분 ≈ 500ms당 1건) 아래로 유지하기 위해 요청 간 간격을 둔다.
// 부하 테스트를 위해 KRAFT_SECURITY_RATE_LIMIT_PER_MINUTE을 올려둔 백엔드라면
// SEED_DELAY_MS=0으로 이 지연을 없앨 수 있다.
const seedDelayMs = Number(process.env.SEED_DELAY_MS ?? 600);
for (let round = 1; round <= count; round++) {
  await upsertRound(round);
  if (round % 50 === 0) console.log(`  ${round}/${count}`);
  if (seedDelayMs > 0) await sleep(seedDelayMs);
}
console.log(`시딩 완료: ${count}회차, ${((Date.now() - start) / 1000).toFixed(1)}s`);
