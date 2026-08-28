// 추천·저장 번호 도메인 픽스처 — L-01로 backend.mjs에서 분리.

import { LATEST_ROUND } from "./stats.mjs";

// 저장 번호는 기기 토큰 스코프에서 실제로 상태를 갖는다 — 저장·조회·삭제가 서로
// 이어져야 e2e에서 "저장했더니 목록에 보인다"를 확인할 수 있다.
let nextSavedId = 100;
const savedNumbers = [];

// 생성한 추천 세트도 마찬가지로 상태를 갖는다 — 커뮤니티 글쓰기의 추천 첨부
// 선택기가 생성 직후 이 목록을 그대로 읽는다.
let nextRecommendationSetId = 200;
const recommendationSets = [];

/**
 * RSP-12(docs/improvement.md): 이 도메인의 상태는 지금까지 리셋 경로가 없었다 —
 * `__test__/reset`이 stats와 community만 되돌렸다. responsive 트랙은
 * `fullyParallel: true`라 하나의 픽스처 백엔드를 여러 스펙이 공유하므로,
 * `/saved`에 항목을 시드하는 스펙이 생기는 순간 그 항목이 다른 스펙(예:
 * fixed-ui-toast-safe-area.spec.ts)에도 새어 나간다. community가 이미 쓰는
 * 패턴과 같은 형태로 리셋 경로를 만든다.
 */
/**
 * RSP-01(docs/improvement.md): 브라우저 컨텍스트 단위 보관함 시드.
 *
 * `savedNumbers`는 픽스처 프로세스 전역이라, 여기에 POST로 시드하는 방식은
 * `fullyParallel` 트랙에서 근본적으로 불안정하다 — container-squeeze는 chromium과
 * firefox-overflow 두 프로젝트에서 동시에 돌고, 아무 스펙이나 `__test__/reset`을
 * 부르면 남의 시드가 지워진다(실제로 firefox 쪽이 "저장 항목 0개"로 빨개졌다).
 * `e2e-saved` 쿠키가 있으면 그 개수만큼 결정적인 목록을 만들어 돌려주고 전역은
 * 전혀 건드리지 않는다 — community.mjs의 `e2e-nickname`과 같은 패턴이다.
 */
function savedFromCookie(headers) {
  const raw = headers?.cookie;
  if (typeof raw !== "string") return null;
  const match = raw.match(/(?:^|;\s*)e2e-saved=(\d+)/);
  if (match === null) return null;

  const count = Math.min(Number(match[1]), 20);
  return Array.from({ length: count }, (_, index) => ({
    id: 900 + index,
    // 두 자리 번호로 채운다 — 한 자리보다 넓어서 압착을 더 정직하게 드러낸다.
    numbers: [11, 22, 33, 38, 41, 44 - index].sort((a, b) => a - b),
    label: null,
    source: "recommend",
    createdAt: "2026-08-01T15:00:00Z",
  }));
}

export function resetNumbersState() {
  savedNumbers.length = 0;
  recommendationSets.length = 0;
  nextSavedId = 100;
  nextRecommendationSetId = 200;
}

export const routes = [
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
    (_params, requestBody, method, headers) => {
      if (method === "GET") return savedFromCookie(headers) ?? savedNumbers;
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
      if (method !== "GET")
        return { items: [], page: 0, size: 20, totalPages: 0, totalElements: 0 };
      return {
        items: recommendationSets,
        page: 0,
        size: 20,
        totalPages: 1,
        totalElements: recommendationSets.length,
      };
    },
  ],
];

/**
 * 경로 파라미터가 있는 라우트 — ROUTES Map은 정확히 일치하는 경로만 찾으므로
 * `/api/v1/saved/:id` 같은 삭제 경로는 따로 정규식으로 처리한다.
 */
export const dynamicRoutes = [
  {
    pattern: /^\/api\/v1\/saved\/(\d+)$/,
    method: "DELETE",
    handle: (id) => {
      const index = savedNumbers.findIndex((item) => item.id === Number(id));
      if (index !== -1) savedNumbers.splice(index, 1);
      return { status: 204, body: null };
    },
  },
];
