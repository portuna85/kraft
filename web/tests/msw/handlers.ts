import { http, HttpResponse, type RequestHandler } from "msw";

/**
 * MSW 핸들러 — improvement_fe.md §21.7
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

const FREQUENCY_STATS = {
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

const ANONYMOUS_SESSION = {
  loggedIn: false,
  userId: null,
  nickname: null,
  activeProviders: ["google", "naver"],
};

export const handlers: RequestHandler[] = [
  http.get(`${BACKEND}/api/v1/rounds/latest`, () => HttpResponse.json(LATEST_ROUND)),
  http.get(`${BACKEND}/api/v1/rounds/freshness`, () => HttpResponse.json(ROUND_FRESHNESS)),
  http.get(`${BACKEND}/api/v1/stats/frequency`, () => HttpResponse.json(FREQUENCY_STATS)),
  http.get(`${BACKEND}/api/v1/community/posts`, () => HttpResponse.json(EMPTY_POST_PAGE)),
  http.get("/api/v1/community/session", () => HttpResponse.json(ANONYMOUS_SESSION)),
];
