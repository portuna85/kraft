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

// 저장 번호는 기기 토큰 스코프에서 실제로 상태를 갖는다 — 저장·조회·삭제가 서로
// 이어져야 e2e에서 "저장했더니 목록에 보인다"를 확인할 수 있다.
let nextSavedId = 100;
const savedNumbers = [];

// 추천 이력도 마찬가지로 상태를 갖는다 — 생성 후 이력 화면에서 보여야 한다.
let nextRecommendationSetId = 200;
const recommendationSets = [];

const STATUS_INCIDENTS = [
  {
    round: 1150,
    type: "EXTERNAL_COLLECT",
    resolved: true,
    occurredAt: "2026-08-01T09:00:00Z",
    occurrences: 1,
  },
];

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
    "/api/v1/community/session",
    { loggedIn: false, userId: null, nickname: null, activeProviders: ["google", "naver"] },
  ],
  [
    "/api/v1/numbers/recommend",
    (_params, requestBody) => {
      const strategy = requestBody?.strategy ?? "random";
      const items = [
        { position: 0, numbers: [1, 8, 17, 24, 33, 41], score: null, explanationCodes: [] },
        { position: 1, numbers: [3, 12, 19, 26, 35, 44], score: null, explanationCodes: [] },
      ];
      // 실제 백엔드는 추천 생성마다 이력에 한 세트를 함께 남긴다 — 픽스처도 같게
      // 동작해야 "생성했더니 이력에 보인다"를 e2e로 확인할 수 있다.
      recommendationSets.unshift({
        id: nextRecommendationSetId++,
        strategy,
        algorithmVersion: "e2e-fixture",
        historyThroughRound: 1150,
        exclusionPolicyVersion: "e2e-fixture",
        lockedNumbers: requestBody?.lockedNumbers ?? [],
        excludedNumbers: requestBody?.excludedNumbers ?? [],
        createdAt: "2026-08-01T15:00:00Z",
        items,
      });
      return {
        recommendations: items.map((item) => item.numbers),
        strategy,
        algorithmVersion: "e2e-fixture",
        historyThroughRound: 1150,
        historicalExclusionApplied: false,
        exclusionPolicyVersion: "e2e-fixture",
        setId: nextRecommendationSetId - 1,
        items,
        createdAt: "2026-08-01T15:00:00Z",
      };
    },
  ],
  [
    "/api/v1/saved",
    (_params, requestBody, method) => {
      if (method === "GET") return savedNumbers;
      // POST — 새 저장 번호를 만든다(§25.5).
      const id = nextSavedId++;
      const created = {
        id,
        numbers: [...(requestBody?.numbers ?? [])],
        label: null,
        source: "recommend",
        createdAt: "2026-08-01T15:00:00Z",
      };
      savedNumbers.push(created);
      return { savedNumber: created, created: true };
    },
  ],
  [
    "/api/v1/saved/matches",
    () =>
      savedNumbers.map((saved) => ({
        savedNumber: saved,
        round: 1150,
        drawDate: "2026-08-01",
        drawNumbers: LATEST_ROUND.numbers,
        bonusNumber: LATEST_ROUND.bonusNumber,
        matchedCount: saved.numbers.filter((n) => LATEST_ROUND.numbers.includes(n)).length,
        bonusMatch: saved.numbers.includes(LATEST_ROUND.bonusNumber),
        prizeTier: "낙첨",
      })),
  ],
  [
    "/api/v1/recommendation-sets",
    (_params, _requestBody, method) => {
      if (method !== "GET") return { items: [], page: 0, totalPages: 0, totalElements: 0 };
      return { items: recommendationSets, page: 0, totalPages: 1, totalElements: recommendationSets.length };
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
  [
    "/api/v1/community/posts/1/comments",
    (_params, requestBody, method) => {
      if (method !== "POST") return COMMENT_PAGE;
      return {
        id: 14,
        postId: 1,
        parentId: requestBody?.parentId ?? null,
        ownerId: 10,
        authorNickname: "테스터",
        content: requestBody?.content ?? "",
        deleted: false,
        createdAt: "2026-08-01T15:30:00Z",
        targetPage: null,
        replies: [],
      };
    },
  ],
]);

/**
 * 경로 파라미터가 있는 라우트 — ROUTES Map은 정확히 일치하는 경로만 찾으므로
 * `/api/v1/saved/:id` 같은 삭제 경로는 따로 정규식으로 처리한다.
 */
const DYNAMIC_ROUTES = [
  {
    pattern: /^\/api\/v1\/saved\/(\d+)$/,
    method: "DELETE",
    handle: (id) => {
      const index = savedNumbers.findIndex((item) => item.id === Number(id));
      if (index !== -1) savedNumbers.splice(index, 1);
      return { status: 204, body: null };
    },
  },
  {
    pattern: /^\/api\/v1\/recommendation-sets\/(\d+)$/,
    method: "DELETE",
    handle: (id) => {
      const index = recommendationSets.findIndex((item) => item.id === Number(id));
      if (index !== -1) recommendationSets.splice(index, 1);
      return { status: 204, body: null };
    },
  },
  {
    pattern: /^\/api\/v1\/community\/comments\/(\d+)$/,
    method: "DELETE",
    handle: () => ({ status: 204, body: null }),
  },
];

/** T-20(핵심 데이터 실패 → 5xx) 검증용 — 테스트가 이 경로로 특정 경로를 고장낼 수 있다. */
let forcedFailurePath = null;

const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? "/", `http://127.0.0.1:${PORT}`);
  const method = request.method ?? "GET";

  // XSRF-TOKEN 쿠키 — 실제 백엔드(Spring Security)가 GET 응답마다 내려주는 것과 같은
  // 역할이다. 이게 없으면 browserMutate가 요청을 보내기 전에 스스로 막는다(§13.4).
  response.setHeader("set-cookie", "XSRF-TOKEN=e2e-fixture-token; Path=/");
  response.setHeader("content-type", "application/json");

  if (url.pathname === "/__test__/fail" && method === "PUT") {
    forcedFailurePath = url.searchParams.get("path");
    response.statusCode = 204;
    response.end();
    return;
  }
  if (url.pathname === "/__test__/reset" && method === "POST") {
    forcedFailurePath = null;
    response.statusCode = 204;
    response.end();
    return;
  }

  if (forcedFailurePath !== null && url.pathname === forcedFailurePath) {
    response.statusCode = 503;
    response.end(JSON.stringify({ code: "FORCED_FAILURE", message: "픽스처가 강제한 실패." }));
    return;
  }

  let requestBody = null;
  if (method === "POST" || method === "PUT") {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    const raw = Buffer.concat(chunks).toString("utf8");
    if (raw.length > 0) requestBody = JSON.parse(raw);
  }

  const dynamic = DYNAMIC_ROUTES.find(
    (route) => route.method === method && route.pattern.test(url.pathname),
  );
  if (dynamic !== undefined) {
    const [, id] = url.pathname.match(dynamic.pattern);
    const { status, body } = dynamic.handle(id);
    response.statusCode = status;
    response.end(body === null ? undefined : JSON.stringify(body));
    return;
  }

  const entry = ROUTES.get(url.pathname);
  if (entry === undefined) {
    response.statusCode = 404;
    response.end(
      JSON.stringify({ code: "NOT_FOUND", message: `픽스처에 없는 경로: ${url.pathname}` }),
    );
    return;
  }

  // 쿼리나 본문, 메서드에 따라 응답이 달라지는 경로는 함수로 둔다.
  const body = typeof entry === "function" ? entry(url.searchParams, requestBody, method) : entry;

  response.statusCode = 200;
  response.end(JSON.stringify(body));
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`[fixture-backend] http://127.0.0.1:${PORT}`);
});
