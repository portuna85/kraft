// 운영 전환 전 성능 예산과 대조할 기준선을 재는
// k6 스크립트. HikariCP maximum-pool-size=5(운영과 동일, application.yml)를 넘는 동시성으로
// 돌리면 API 자체 성능이 아니라 커넥션 풀 고갈만 측정하게 된다(B2/B3 동시성 테스트에서 이미
// 확인된 제약) — 기본 VU 수를 그 이하로 제한한다.
//
// 실행: k6 run --env BASE_URL=http://localhost:8080 scripts/load-test/recommend-and-community.js
// 로컬/스테이징 전용 — 운영 서버에는 절대 직접 부하를 걸지 않는다.
import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const VUS = Number(__ENV.VUS || 4);
const DURATION = __ENV.DURATION || "30s";
const SCENARIO_VUS = Math.max(1, Math.floor(VUS / 4));
const policyViolationRate = new Rate("recommendation_policy_violation");
const storedNumberFailureRate = new Rate("stored_number_flow_failure");
const historyCheckDuration = new Trend("history_check_duration", true);

export const options = {
  scenarios: {
    recommend: {
      executor: "constant-vus",
      exec: "recommend",
      vus: SCENARIO_VUS,
      duration: DURATION,
    },
    communityList: {
      executor: "constant-vus",
      exec: "communityList",
      vus: SCENARIO_VUS,
      duration: DURATION,
    },
    communitySearch: {
      executor: "constant-vus",
      exec: "communitySearch",
      vus: SCENARIO_VUS,
      duration: DURATION,
    },
    storedNumbers: {
      executor: "constant-vus",
      exec: "storedNumbers",
      vus: SCENARIO_VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    // 정상 이력 캐시 기준 추천 API p95 ≤300ms.
    "http_req_duration{endpoint:recommend}": ["p(95)<300"],
    // 공개 피드 API(캐시 미적중 기준) p95 ≤350ms.
    "http_req_duration{endpoint:community_list}": ["p(95)<350"],
    // 검색 API(정의된 데이터 기준) p95 ≤500ms.
    "http_req_duration{endpoint:community_search}": ["p(95)<500"],
    "http_req_duration{endpoint:stored_numbers}": ["p(95)<500"],
    recommendation_policy_violation: ["rate==0"],
    stored_number_flow_failure: ["rate==0"],
    history_check_duration: ["p(95)<300"],
    http_req_failed: ["rate<0.01"],
  },
};

const STRATEGIES = ["random", "balanced", "reduce_shared_winner_risk"];

export function recommend() {
  const strategy = STRATEGIES[Math.floor(Math.random() * STRATEGIES.length)];
  const deviceToken = `kraft-load-test-recommend-device-${__VU}`.padEnd(40, "0");
  const res = http.post(
    `${BASE_URL}/api/v1/numbers/recommend`,
    JSON.stringify({ count: 1, strategy }),
    {
      headers: {
        "Content-Type": "application/json",
        "X-Device-Token": deviceToken,
      },
      tags: { endpoint: "recommend" },
    }
  );
  let body;
  try {
    body = res.json();
  } catch {
    body = null;
  }
  const recommendation = body?.recommendations?.[0];
  const policyMetadataValid =
    body?.historicalExclusionApplied === true &&
    Number.isInteger(body?.historyThroughRound) &&
    body.historyThroughRound > 0 &&
    typeof body?.exclusionPolicyVersion === "string" &&
    body.exclusionPolicyVersion.length > 0;
  const responseValid = check(res, {
    "recommend 200": (r) => r.status === 200,
    "history exclusion metadata present": () => policyMetadataValid,
    "recommendation has six numbers": () =>
      Array.isArray(recommendation) && recommendation.length === 6,
  });

  let historicalWinner = true;
  if (Array.isArray(recommendation) && recommendation.length === 6) {
    const query = recommendation.map((number) => `numbers=${number}`).join("&");
    const historyResponse = http.get(`${BASE_URL}/api/v1/numbers/check?${query}`, {
      tags: { endpoint: "history_check" },
    });
    historyCheckDuration.add(historyResponse.timings.duration);
    try {
      historicalWinner =
        historyResponse.status !== 200 || historyResponse.json()?.wonFirstPrize !== false;
    } catch {
      historicalWinner = true;
    }
    check(historyResponse, {
      "recommended combination is not historical first prize": () => !historicalWinner,
    });
  }
  policyViolationRate.add(!responseValid || historicalWinner);
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

export function storedNumbers() {
  const deviceToken = `kraft-load-test-saved-device-${__VU}`.padEnd(40, "0");
  const headers = {
    "Content-Type": "application/json",
    "X-Device-Token": deviceToken,
  };
  const saveResponse = http.post(
    `${BASE_URL}/api/v1/saved`,
    JSON.stringify({
      numbers: [1, 7, 14, 22, 31, 45],
      label: "k6",
      source: "LOAD_TEST",
    }),
    { headers, tags: { endpoint: "stored_numbers" } }
  );

  let savedId;
  try {
    savedId = saveResponse.json()?.item?.id;
  } catch {
    savedId = undefined;
  }
  const saveValid = check(saveResponse, {
    "saved number accepted": (r) => r.status === 200 || r.status === 201,
    "saved number id returned": () => Number.isInteger(savedId),
  });

  let deleteValid = false;
  if (Number.isInteger(savedId)) {
    const deleteResponse = http.del(`${BASE_URL}/api/v1/saved/${savedId}`, null, {
      headers,
      tags: { endpoint: "stored_numbers" },
    });
    deleteValid = check(deleteResponse, {
      "saved number deleted": (r) => r.status === 204,
    });
  }
  storedNumberFailureRate.add(!saveValid || !deleteValid);
  sleep(0.2);
}
