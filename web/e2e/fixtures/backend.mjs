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

const POST_DETAIL = {
  id: 1,
  ownerId: 10,
  authorNickname: "테스터",
  title: "첫 글입니다",
  content: "본문 첫 줄\n본문 둘째 줄",
  category: "GENERAL",
  status: "PUBLISHED",
  version: 0,
  createdAt: "2026-08-01T12:00:00Z",
  updatedAt: "2026-08-01T12:00:00Z",
  likeCount: 3,
  commentCount: 2,
  viewCount: 41,
  recommendationAttachment: null,
};

// 답글이 상위 댓글 안에 들어 있고, tombstone 댓글이 자리를 지키는지 확인하기 위한 형태다.
const COMMENT_PAGE = {
  topLevel: [
    {
      id: 11,
      postId: 1,
      parentId: null,
      ownerId: 10,
      authorNickname: "댓글쓴이",
      content: "첫 댓글",
      deleted: false,
      createdAt: "2026-08-01T13:00:00Z",
      targetPage: null,
      replies: [
        {
          id: 12,
          postId: 1,
          parentId: 11,
          ownerId: 20,
          authorNickname: "답글쓴이",
          content: "답글입니다",
          deleted: false,
          createdAt: "2026-08-01T13:30:00Z",
          targetPage: null,
          replies: [],
        },
      ],
    },
    {
      id: 13,
      postId: 1,
      parentId: null,
      ownerId: null,
      authorNickname: "(삭제됨)",
      content: "삭제된 댓글입니다.",
      deleted: true,
      createdAt: "2026-08-01T14:00:00Z",
      targetPage: null,
      replies: [],
    },
  ],
  totalTopLevelComments: 2,
  page: 0,
  size: 50,
  totalPages: 1,
};

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
  ["/api/v1/community/posts", EMPTY_POST_PAGE],
  ["/api/v1/community/posts/1", POST_DETAIL],
  ["/api/v1/community/posts/1/comments", COMMENT_PAGE],
  [
    "/api/v1/community/session",
    { loggedIn: false, userId: null, nickname: null, activeProviders: ["google", "naver"] },
  ],
]);

const server = createServer(async (request, response) => {
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

  let requestBody = null;
  if (request.method === "POST") {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    const raw = Buffer.concat(chunks).toString("utf8");
    if (raw.length > 0) requestBody = JSON.parse(raw);
  }

  // 쿼리나 본문에 따라 응답이 달라지는 경로는 함수로 둔다.
  const body = typeof entry === "function" ? entry(url.searchParams, requestBody) : entry;

  response.statusCode = 200;
  response.end(JSON.stringify(body));
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`[fixture-backend] http://127.0.0.1:${PORT}`);
});
