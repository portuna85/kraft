// Phase 6(운영 전환과 정리): docs/improvement_gpt.md §16.5 성능 예산과 대조할 기준선을 재는
// k6 스크립트. HikariCP maximum-pool-size=5(운영과 동일, application.yml)를 넘는 동시성으로
// 돌리면 API 자체 성능이 아니라 커넥션 풀 고갈만 측정하게 된다(B2/B3 동시성 테스트에서 이미
// 확인된 제약) — 기본 VU 수를 그 이하로 제한한다.
//
// 실행: k6 run --env BASE_URL=http://localhost:8080 scripts/load-test/recommend-and-community.js
// 로컬/스테이징 전용 — 운영 서버에는 절대 직접 부하를 걸지 않는다.
import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const VUS = Number(__ENV.VUS || 4);
const DURATION = __ENV.DURATION || "30s";

export const options = {
  scenarios: {
    recommend: {
      executor: "constant-vus",
      exec: "recommend",
      vus: VUS,
      duration: DURATION,
    },
    communityList: {
      executor: "constant-vus",
      exec: "communityList",
      vus: VUS,
      duration: DURATION,
    },
    communitySearch: {
      executor: "constant-vus",
      exec: "communitySearch",
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    // §16.5 성능 예산 — 정상 이력 캐시 기준 추천 API p95 ≤300ms.
    "http_req_duration{endpoint:recommend}": ["p(95)<300"],
    // §16.5 — 공개 피드 API(캐시 미적중 기준) p95 ≤350ms.
    "http_req_duration{endpoint:community_list}": ["p(95)<350"],
    // §16.5 — 검색 API(정의된 데이터 기준) p95 ≤500ms.
    "http_req_duration{endpoint:community_search}": ["p(95)<500"],
  },
};

const STRATEGIES = ["random", "balanced", "reduce_shared_winner_risk"];

export function recommend() {
  const strategy = STRATEGIES[Math.floor(Math.random() * STRATEGIES.length)];
  const res = http.post(
    `${BASE_URL}/api/v1/numbers/recommend`,
    JSON.stringify({ count: 1, strategy }),
    { headers: { "Content-Type": "application/json" }, tags: { endpoint: "recommend" } }
  );
  check(res, { "recommend 200": (r) => r.status === 200 });
  sleep(0.2);
}

export function communityList() {
  const res = http.get(`${BASE_URL}/api/v1/community/posts?page=0&size=20`, {
    tags: { endpoint: "community_list" },
  });
  check(res, { "list 200": (r) => r.status === 200 });
  sleep(0.2);
}

export function communitySearch() {
  const query = encodeURIComponent("로또");
  const res = http.get(`${BASE_URL}/api/v1/community/posts?page=0&size=20&query=${query}`, {
    tags: { endpoint: "community_search" },
  });
  check(res, { "search 200": (r) => r.status === 200 });
  sleep(0.2);
}
