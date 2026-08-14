// 추천·저장 번호 도메인 픽스처 — L-01로 backend.mjs에서 분리.

import { LATEST_ROUND } from "./stats.mjs";

// 저장 번호는 기기 토큰 스코프에서 실제로 상태를 갖는다 — 저장·조회·삭제가 서로
// 이어져야 e2e에서 "저장했더니 목록에 보인다"를 확인할 수 있다.
let nextSavedId = 100;
const savedNumbers = [];

// 추천 이력도 마찬가지로 상태를 갖는다 — 생성 후 이력 화면에서 보여야 한다.
let nextRecommendationSetId = 200;
const recommendationSets = [];

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
      return {
        items: recommendationSets,
        page: 0,
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
  {
    pattern: /^\/api\/v1\/recommendation-sets\/(\d+)$/,
    method: "DELETE",
    handle: (id) => {
      const index = recommendationSets.findIndex((item) => item.id === Number(id));
      if (index !== -1) recommendationSets.splice(index, 1);
      return { status: 204, body: null };
    },
  },
];
