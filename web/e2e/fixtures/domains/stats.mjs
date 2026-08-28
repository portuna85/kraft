// 회차·통계·운영 상태 도메인 픽스처 — L-01로 backend.mjs에서 분리.

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

export const BASE_LATEST_ROUND = { ...LATEST_ROUND };

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
  ["/api/v1/status/incidents", STATUS_INCIDENTS],
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
