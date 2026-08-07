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
  ["/api/v1/community/posts", EMPTY_POST_PAGE],
  [
    "/api/v1/community/session",
    { loggedIn: false, userId: null, nickname: null, activeProviders: ["google", "naver"] },
  ],
]);

const server = createServer((request, response) => {
  const path = (request.url ?? "").split("?")[0] ?? "";
  const body = ROUTES.get(path);

  response.setHeader("content-type", "application/json");
  if (body === undefined) {
    response.statusCode = 404;
    response.end(JSON.stringify({ code: "NOT_FOUND", message: `픽스처에 없는 경로: ${path}` }));
    return;
  }

  response.statusCode = 200;
  response.end(JSON.stringify(body));
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`[fixture-backend] http://127.0.0.1:${PORT}`);
});
