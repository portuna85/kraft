// 콘텐츠 e2e 트랙 전용 경량 백엔드 픽스처. playwright.content.config.ts가 이 서버를
// KRAFT_BACKEND_INTERNAL_URL로 가리켜, 서버 컴포넌트가 백엔드를 직접 호출하는
// 페이지(/, /frequency, /stats, /companion)가 폴백/에러 화면이 아니라 실제 콘텐츠를
// 렌더하게 한다. 기존 e2e/*.spec.ts(백엔드 없음 전제)는 별도 설정(playwright.config.ts,
// 포트 59999)을 그대로 쓰므로 이 픽스처의 영향을 받지 않는다.
import { createServer } from "node:http";

const PORT = Number(process.env.E2E_FIXTURE_BACKEND_PORT ?? 4101);

const ROUNDS_LATEST = {
  round: 1189,
  drawDate: "2026-07-18",
  numbers: [3, 11, 19, 24, 33, 41],
  bonusNumber: 7,
  firstPrizeAmount: 2145678900,
  secondPrize: 51234567,
  secondWinners: 42,
  totalSales: 98765432100,
  firstAccumAmount: 2145678900,
};

const ROUNDS_FRESHNESS = {
  latestRound: 1189,
  latestDrawDate: "2026-07-18",
  fresh: true,
  checkedAt: new Date().toISOString(),
};

const STATUS_INCIDENTS = [
  {
    round: 1188,
    type: "COLLECTION_DELAY",
    resolved: true,
    occurredAt: "2026-07-11T09:00:00Z",
    occurrences: 1,
  },
];

function ballFrequencies() {
  return Array.from({ length: 45 }, (_, i) => ({
    ballNumber: i + 1,
    frequency: 100 + ((i * 7) % 40),
    lastRound: 1189 - (i % 10),
  }));
}

const STATS_FREQUENCY = {
  totalRounds: 1189,
  frequencies: ballFrequencies(),
  topSix: { balls: ballFrequencies().slice(0, 6), wonFirstPrize: false, firstPrizeHistory: [] },
  bottomSix: { balls: ballFrequencies().slice(-6), wonFirstPrize: false, firstPrizeHistory: [] },
};

const STATS_PATTERNS = {
  totalRounds: 1189,
  oddCounts: [0, 1, 2, 3, 4, 5, 6].map((k) => ({ bucketKey: String(k), count: 20 + k * 15 })),
  highCounts: [0, 1, 2, 3, 4, 5, 6].map((k) => ({ bucketKey: String(k), count: 18 + k * 14 })),
  sumBuckets: ["21-65", "66-110", "111-155", "156-200", "201-255"].map((key, i) => ({
    bucketKey: key,
    count: 40 + i * 60,
  })),
};

function companionPairs() {
  const pairs = [];
  for (let a = 1; a <= 20 && pairs.length < 60; a++) {
    for (let b = a + 1; b <= 20 && pairs.length < 60; b++) {
      pairs.push({ ballA: a, ballB: b, coCount: 30 - (pairs.length % 20) });
    }
  }
  return pairs;
}

const STATS_COMPANION = {
  totalRounds: 1189,
  topPairs: companionPairs(),
};

const COMMUNITY_POST = {
  id: 1,
  ownerId: 42,
  authorNickname: "글쓴이",
  title: "테스트 게시글",
  content: "첫 번째 줄\n두 번째 줄",
  version: 0,
  createdAt: "2026-07-01T00:00:00Z",
  updatedAt: "2026-07-01T00:00:00Z",
};

const COMMUNITY_POSTS_PAGE = {
  items: [COMMUNITY_POST],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

// R-35: 극단 입력 — 공백 없는 200자 제목과 URL 덩어리 본문이 좁은 뷰포트에서
// 오버플로를 유발하지 않는지 검사하기 위한 픽스처(community-responsive.spec.ts).
const COMMUNITY_POST_EXTREME = {
  id: 2,
  ownerId: 42,
  authorNickname: "글쓴이",
  title: "가".repeat(200),
  content: "https://example.com/" + "a".repeat(180) + "\n" + "나".repeat(300),
  version: 0,
  createdAt: "2026-07-01T00:00:00Z",
  updatedAt: "2026-07-01T00:00:00Z",
};

const ROUTES = {
  "/api/v1/rounds/latest": ROUNDS_LATEST,
  "/api/v1/rounds/freshness": ROUNDS_FRESHNESS,
  "/api/v1/status/incidents": STATUS_INCIDENTS,
  "/api/v1/stats/frequency": STATS_FREQUENCY,
  "/api/v1/stats/patterns": STATS_PATTERNS,
  "/api/v1/stats/companion": STATS_COMPANION,
  // 커뮤니티 공개 목록·상세 ISR이 사용자 세션 정보를 절대 embed하지 않는지
  // 증명하는 e2e 전용 픽스처(community-privacy.spec.ts).
  "/api/v1/community/posts": COMMUNITY_POSTS_PAGE,
  "/api/v1/community/posts/1": COMMUNITY_POST,
  "/api/v1/community/posts/2": COMMUNITY_POST_EXTREME,
};

const server = createServer((req, res) => {
  const requestPath = (req.url ?? "").split("?")[0];
  // playwright.content.config.ts의 webServer 준비 확인(GET /)용 — 2xx가 아니면
  // "아직 안 떴다"고 보고 계속 재시도하므로 헬스체크 경로를 따로 둔다.
  if (requestPath === "/") {
    res.writeHead(200, { "content-type": "text/plain" });
    res.end("ok");
    return;
  }
  const body = ROUTES[requestPath];
  if (!body) {
    res.writeHead(404, { "content-type": "application/json" });
    res.end(JSON.stringify({ code: "NOT_FOUND", message: `no fixture for ${requestPath}` }));
    return;
  }
  res.writeHead(200, { "content-type": "application/json" });
  res.end(JSON.stringify(body));
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`[e2e content fixture backend] listening on http://127.0.0.1:${PORT}`);
});
