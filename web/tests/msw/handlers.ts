import { http, HttpResponse, type RequestHandler } from "msw";

/**
 * MSW 핸들러
 *
 * 응답은 각 entity의 Valibot 스키마를 만족하는 값으로만 만든다. 목이 실제 계약과
 * 어긋나면 통합 테스트가 "지나가는데 프로덕션에서 터지는" 가짜 안전망이 된다.
 *
 * 서버 컴포넌트가 쓰는 `serverFetch`는 `KRAFT_BACKEND_INTERNAL_URL`(테스트 환경
 * 기본값 `http://backend:8080`)을 절대 URL로 호출한다 — 핸들러도 그 오리진으로 등록한다.
 * 브라우저 쪽 `browserQuery`/`browserMutate`는 상대 경로를 쓰므로 경로만으로 등록한다.
 */
const BACKEND = "http://backend:8080";

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

const ROUND_FRESHNESS = {
  latestRound: 1150,
  latestDrawDate: "2026-08-01",
  fresh: true,
  checkedAt: "2026-08-01T12:00:00Z",
};

const EMPTY_POST_PAGE = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

const ANONYMOUS_SESSION = {
  loggedIn: false,
  userId: null,
  nickname: null,
  activeProviders: ["google", "naver"],
};

const SAVED_NUMBER = {
  id: 1,
  numbers: [1, 8, 17, 24, 33, 41],
  label: null,
  source: "recommend",
  createdAt: "2026-08-01T12:00:00Z",
};

const COMMUNITY_POST = {
  id: 1,
  ownerId: 10,
  authorNickname: "나",
  title: "제목",
  content: "내용",
  category: "GENERAL",
  status: "PUBLISHED",
  version: 1,
  createdAt: "2026-08-01T12:00:00Z",
  updatedAt: "2026-08-01T12:00:00Z",
  likeCount: 0,
  commentCount: 0,
  viewCount: 0,
  recommendationAttachment: null,
};

const COMMUNITY_COMMENT = {
  id: 1,
  postId: 1,
  parentId: null,
  ownerId: 10,
  authorNickname: "나",
  content: "댓글",
  deleted: false,
  createdAt: "2026-08-01T12:00:00Z",
  targetPage: null,
  replies: [
    {
      id: 2,
      postId: 1,
      parentId: 1,
      ownerId: 20,
      authorNickname: "다른사람",
      content: "답글",
      deleted: false,
      createdAt: "2026-08-01T12:30:00Z",
      targetPage: null,
      replies: [],
    },
  ],
};

const STATUS_INCIDENTS = [
  {
    round: 1150,
    type: "EXTERNAL_COLLECT",
    resolved: true,
    occurredAt: "2026-08-01T12:00:00Z",
    occurrences: 1,
  },
];

const RECOMMENDATION_SET_PAGE = {
  items: [
    {
      id: 1,
      strategy: "random",
      createdAt: "2026-08-01T12:00:00Z",
      historyThroughRound: 1149,
      items: [{ position: 0, numbers: [1, 2, 3, 4, 5, 6], score: null, explanationCodes: [] }],
    },
  ],
  page: 0,
  totalPages: 1,
  totalElements: 1,
};

export const handlers: RequestHandler[] = [
  http.get(`${BACKEND}/api/v1/rounds/latest`, () => HttpResponse.json(LATEST_ROUND)),
  http.get(`${BACKEND}/api/v1/rounds/freshness`, () => HttpResponse.json(ROUND_FRESHNESS)),
  http.get(`${BACKEND}/api/v1/community/posts`, () => HttpResponse.json(EMPTY_POST_PAGE)),
  http.get("/api/v1/community/session", () => HttpResponse.json(ANONYMOUS_SESSION)),

  // 보관함 — 저장·조회·삭제. created:false로 중복 저장 응답도 검증한다(§25.5).
  http.get("/api/v1/saved", () => HttpResponse.json([SAVED_NUMBER])),
  http.get("/api/v1/community/me/saved-numbers", () => HttpResponse.json([SAVED_NUMBER])),
  http.post("/api/v1/saved", async ({ request }) => {
    const body = (await request.json()) as { numbers: number[] };
    return HttpResponse.json({
      savedNumber: { ...SAVED_NUMBER, numbers: body.numbers },
      created: true,
    });
  }),
  http.post("/api/v1/community/me/saved-numbers", async ({ request }) => {
    const body = (await request.json()) as { numbers: number[] };
    return HttpResponse.json({
      savedNumber: { ...SAVED_NUMBER, numbers: body.numbers },
      created: true,
    });
  }),
  http.delete("/api/v1/saved/:id", () => new HttpResponse(null, { status: 204 })),
  http.delete(
    "/api/v1/community/me/saved-numbers/:id",
    () => new HttpResponse(null, { status: 204 }),
  ),

  // 댓글 — 상위·답글 2단 구조를 실제 스키마로 왕복시킨다(§25.6).
  http.get(`${BACKEND}/api/v1/community/posts/:postId/comments`, () =>
    HttpResponse.json({
      topLevel: [COMMUNITY_COMMENT],
      totalTopLevelComments: 1,
      page: 0,
      size: 50,
      totalPages: 1,
    }),
  ),
  http.get("/api/v1/community/posts/:postId/comments", () =>
    HttpResponse.json({
      topLevel: [COMMUNITY_COMMENT],
      totalTopLevelComments: 1,
      page: 0,
      size: 50,
      totalPages: 1,
    }),
  ),
  http.post("/api/v1/community/posts/:postId/comments", async ({ request }) => {
    const body = (await request.json()) as { content: string; parentId: number | null };
    return HttpResponse.json({
      ...COMMUNITY_COMMENT,
      id: 3,
      content: body.content,
      parentId: body.parentId,
      replies: [],
    });
  }),
  http.delete("/api/v1/community/comments/:id", () => new HttpResponse(null, { status: 204 })),

  // 신고 — 응답 형태는 검증하지 않지만 200을 낸다(§3.6).
  http.post("/api/v1/community/reports", () => HttpResponse.json({ accepted: true })),

  // 게시글 작성·수정 — 두 번째 PUT은 409로, 낙관적 잠금 충돌 경로를 검증한다(§16.4).
  http.post("/api/v1/community/posts", async ({ request }) => {
    const body = (await request.json()) as { title: string; content: string; category: string };
    return HttpResponse.json({ ...COMMUNITY_POST, ...body });
  }),
  http.get("/api/v1/community/posts/:id", () => HttpResponse.json(COMMUNITY_POST)),
  http.put("/api/v1/community/posts/:id", async ({ request }) => {
    const body = (await request.json()) as { title: string; content: string };
    return HttpResponse.json({ ...COMMUNITY_POST, ...body, version: 2 });
  }),

  // 장애 이력
  http.get(`${BACKEND}/api/v1/status/incidents`, () => HttpResponse.json(STATUS_INCIDENTS)),

  // 생성한 추천 세트 목록 — 커뮤니티 글쓰기의 추천 첨부 선택기가 사용한다.
  http.get("/api/v1/recommendation-sets", () => HttpResponse.json(RECOMMENDATION_SET_PAGE)),
  http.get("/api/v1/community/me/recommendation-sets", () =>
    HttpResponse.json(RECOMMENDATION_SET_PAGE),
  ),
];
