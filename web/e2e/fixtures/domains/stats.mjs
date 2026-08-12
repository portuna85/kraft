// 회차·통계·운영 상태 도메인 픽스처 — L-01(improvement_codex.md)로 backend.mjs에서 분리.

export const LATEST_ROUND = {
  round: 1150,
  drawDate: "2026-08-01",
  numbers: [3, 11, 24, 30, 38, 44],
  bonusNumber: 7,
  firstPrizeAmount: 2_100_000_000,
  secondPrize: 60_000_000,
  secondWinners: 35,
  totalSales: 110_000_000_000,
  firstAccumAmount: 2_100_000_000,
};

function ball(ballNumber) {
  return { ballNumber, frequency: 150 + (ballNumber % 7), lastRound: 1150 - (ballNumber % 30) };
}

const FREQUENCY = {
  totalRounds: 1150,
  frequencies: Array.from({ length: 45 }, (_, index) => ball(index + 1)),
  topSix: {
    balls: [1, 2, 3, 4, 5, 6].map(ball),
    wonFirstPrize: false,
    firstPrizeHistory: [],
  },
  bottomSix: {
    balls: [40, 41, 42, 43, 44, 45].map(ball),
    wonFirstPrize: false,
    firstPrizeHistory: [],
  },
};

// 합계 구간은 백엔드가 bucketKey 문자열 오름차순으로 보낸다 — "111-155"가 "21-65"보다
// 앞선다. 픽스처도 그 순서로 두어야 화면의 재정렬이 실제로 필요한지 확인할 수 있다.
const PATTERNS = {
  totalRounds: 1150,
  oddCounts: Array.from({ length: 7 }, (_, index) => ({
    bucketKey: String(index),
    count: [10, 60, 210, 390, 300, 150, 30][index],
  })),
  highCounts: Array.from({ length: 7 }, (_, index) => ({
    bucketKey: String(index),
    count: [12, 70, 220, 380, 290, 148, 30][index],
  })),
  sumBuckets: [
    { bucketKey: "111-155", count: 470 },
    { bucketKey: "156-200", count: 300 },
    { bucketKey: "201-255", count: 40 },
    { bucketKey: "21-65", count: 40 },
    { bucketKey: "66-110", count: 300 },
  ],
};

// 백엔드는 990쌍을 전부 보낸다(COMPANION_TOP_LIMIT = 990). 화면이 상위 50쌍만
// 렌더하는지 확인하려면 픽스처도 50쌍보다 많아야 한다.
const COMPANION_PAIRS = [];
for (let a = 1; a <= 45; a += 1) {
  for (let b = a + 1; b <= 45; b += 1) {
    COMPANION_PAIRS.push({ ballA: a, ballB: b, coCount: 20 - ((a + b) % 12) });
  }
}
COMPANION_PAIRS.sort((x, y) => y.coCount - x.coCount || x.ballA - y.ballA || x.ballB - y.ballB);

const STATUS_INCIDENTS = [
  {
    round: 1150,
    type: "EXTERNAL_COLLECT",
    resolved: true,
    occurredAt: "2026-08-01T09:00:00Z",
    occurrences: 1,
  },
];

export const routes = [
  ["/api/v1/rounds/latest", LATEST_ROUND],
  [
    "/api/v1/rounds/freshness",
    {
      latestRound: 1150,
      latestDrawDate: "2026-08-01",
      fresh: true,
      checkedAt: "2026-08-01T12:00:00Z",
    },
  ],
  ["/api/v1/stats/frequency", FREQUENCY],
  ["/api/v1/stats/patterns", PATTERNS],
  ["/api/v1/status/incidents", STATUS_INCIDENTS],
  [
    "/api/v1/stats/companion",
    // 실제 백엔드처럼 ball 파라미터가 있으면 그 번호가 낀 쌍만 돌려준다. 화면이 필터를
    // 서버에 맡기는지(상위 50 밖도 매칭되는지) 확인하려면 픽스처도 같게 동작해야 한다.
    (params) => {
      const raw = params.get("ball");
      if (raw === null) return { totalRounds: 1150, topPairs: COMPANION_PAIRS };
      const ball = Number(raw);
      return {
        totalRounds: 1150,
        topPairs: COMPANION_PAIRS.filter((pair) => pair.ballA === ball || pair.ballB === ball),
      };
    },
  ],
  [
    // 백엔드는 이것을 POST로 받는다 — 번호 6개를 본문에 실어야 하기 때문이다.
    "/api/v1/stats/analysis",
    (_params, requestBody) => {
      const numbers = [...(requestBody?.numbers ?? [])].sort((a, b) => a - b);
      const odd = numbers.filter((n) => n % 2 !== 0).length;
      const high = numbers.filter((n) => n >= 23).length;
      const sum = numbers.reduce((total, n) => total + n, 0);
      const sumBucket =
        sum < 66
          ? "21-65"
          : sum < 111
            ? "66-110"
            : sum < 156
              ? "111-155"
              : sum < 201
                ? "156-200"
                : "201-255";
      // 1번이 들어간 조합이면 1등 이력이 있는 것으로 꾸며 두 갈래를 다 확인할 수 있게 한다.
      const won = numbers.includes(1);
      return {
        numbers,
        oddCount: odd,
        evenCount: numbers.length - odd,
        lowCount: numbers.length - high,
        highCount: high,
        sumOfNumbers: sum,
        sumBucket,
        consecutivePairCount: numbers.filter((n, i) => i > 0 && n - numbers[i - 1] === 1).length,
        rangeDistribution: [
          { range: "1-10", count: numbers.filter((n) => n <= 10).length },
          { range: "11-20", count: numbers.filter((n) => n > 10 && n <= 20).length },
          { range: "21-30", count: numbers.filter((n) => n > 20 && n <= 30).length },
          { range: "31-45", count: numbers.filter((n) => n > 30).length },
        ],
        wonFirstPrize: won,
        firstPrizeHistory: won
          ? [{ round: 812, drawDate: "2018-05-12", firstPrizeAmount: 1_800_000_000 }]
          : [],
      };
    },
  ],
];

export const dynamicRoutes = [];
