// E2E용 최소 백엔드 픽스처.
//
// 실제 백엔드를 띄우지 않고도 RSC 라우트가 렌더되게 한다. 응답은 Valibot 스키마를
// 만족하는 최소값이며, **계약과 어긋나면 스키마가 막아 준다** — 픽스처가 슬금슬금
// 실제 계약에서 멀어지는 것을 그 자리에서 알 수 있다(§21.7과 같은 취지).
//
//   PORT=4110 node e2e/fixtures/backend.mjs
import { createServer } from "node:http";

const PORT = Number(process.env.PORT ?? 4110);

const LATEST_ROUND = {
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

const FREQUENCY = {
  totalRounds: 1150,
  frequencies: Array.from({ length: 45 }, (_, index) => ({
    ballNumber: index + 1,
    frequency: 150 + (index % 7),
    lastRound: 1150 - (index % 30),
  })),
  topSix: { balls: [], wonFirstPrize: false, firstPrizeHistory: [] },
  bottomSix: { balls: [], wonFirstPrize: false, firstPrizeHistory: [] },
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

const EMPTY_POST_PAGE = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

const ROUTES = new Map([
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
  ["/api/v1/community/posts", EMPTY_POST_PAGE],
  [
    "/api/v1/community/session",
    { loggedIn: false, userId: null, nickname: null, activeProviders: ["google", "naver"] },
  ],
]);

const server = createServer((request, response) => {
  const url = new URL(request.url ?? "/", `http://127.0.0.1:${PORT}`);
  const entry = ROUTES.get(url.pathname);

  response.setHeader("content-type", "application/json");
  if (entry === undefined) {
    response.statusCode = 404;
    response.end(
      JSON.stringify({ code: "NOT_FOUND", message: `픽스처에 없는 경로: ${url.pathname}` }),
    );
    return;
  }

  // 쿼리에 따라 응답이 달라지는 경로는 함수로 둔다.
  const body = typeof entry === "function" ? entry(url.searchParams) : entry;

  response.statusCode = 200;
  response.end(JSON.stringify(body));
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`[fixture-backend] http://127.0.0.1:${PORT}`);
});
