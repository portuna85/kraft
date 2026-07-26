// B-06 완료 조건이 요구하는 커버리지: 추천 수·전략, 통계 cold/warm, 저장번호 동시성,
// 커뮤니티 페이지네이션. 수집(외부 fetch)·요약 재구축은 외부 네트워크나 스케줄러
// 트리거가 필요해 이 HTTP 부하 시나리오에서는 다루지 않는다(각각 B-04/운영 스크립트
// 영역 — 별도로 남긴다).
//
// 이 파일을 만들며 실제로 발견한 것(추측 아님, 서버 로그로 확인): local 프로파일의
// Hikari maximum-pool-size=5는 동시 쓰기 concurrency가 "정확히 5"일 때도 여유가 0이라
// 간헐적으로 커넥션 풀이 고갈된다(HikariPool-1 ... active=5, idle=0 → 500). 자세한
// 내용은 saved-numbers-create-contended 시나리오의 주석 참고.
import crypto from "node:crypto";

function randomDeviceToken() {
  return crypto.randomBytes(32).toString("hex"); // 64자, DeviceTokenSupport 허용 범위(32~128) 안
}

// 저장번호 "동시성"을 의미 있게 재현하려면 서로 무관한 무작위 토큰이 아니라, 같은
// 클라이언트(같은 잠금 행)에 여러 요청이 동시에 몰려야 한다. 고정된 소수의 토큰
// 풀로 경쟁을 강제한다.
const SAVED_TOKEN_POOL = Array.from({ length: 5 }, randomDeviceToken);

function pickSavedToken() {
  return SAVED_TOKEN_POOL[Math.floor(Math.random() * SAVED_TOKEN_POOL.length)];
}

function randomLottoNumbers() {
  const numbers = new Set();
  while (numbers.size < 6) {
    numbers.add(1 + Math.floor(Math.random() * 45));
  }
  return [...numbers];
}

/**
 * @param {string} baseUrl
 * @returns {Array<{ name: string, concurrency: number, durationMs: number, request: () => Promise<{ok:boolean}> }>}
 */
export function buildScenarios(baseUrl) {
  const get = async (path) => {
    const res = await fetch(`${baseUrl}${path}`);
    return { ok: res.ok };
  };

  return [
    {
      name: "rounds-latest",
      concurrency: 10,
      durationMs: 5000,
      request: () => get("/api/v1/rounds/latest"),
    },
    {
      name: "stats-frequency",
      concurrency: 10,
      durationMs: 5000,
      request: () => get("/api/v1/stats/frequency"),
    },
    {
      name: "stats-patterns",
      concurrency: 10,
      durationMs: 5000,
      request: () => get("/api/v1/stats/patterns"),
    },
    {
      name: "stats-companion",
      concurrency: 10,
      durationMs: 5000,
      request: () => get("/api/v1/stats/companion"),
    },
    {
      name: "recommend-random-count1",
      concurrency: 8,
      durationMs: 5000,
      request: async () => {
        const res = await fetch(`${baseUrl}/api/v1/numbers/recommend`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ count: 1, excludedNumbers: [], reduceSharedWinnerRisk: false }),
        });
        return { ok: res.ok };
      },
    },
    {
      name: "recommend-reduce-shared-count5",
      concurrency: 8,
      durationMs: 5000,
      request: async () => {
        const res = await fetch(`${baseUrl}/api/v1/numbers/recommend`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ count: 5, excludedNumbers: [], reduceSharedWinnerRisk: true }),
        });
        return { ok: res.ok };
      },
    },
    {
      name: "community-posts-page",
      concurrency: 10,
      durationMs: 5000,
      request: () => get("/api/v1/community/posts?page=0&size=20"),
    },
    {
      // local 프로파일 Hikari maximum-pool-size는 5(base application.yml 상속, prod만
      // 15로 오버라이드). 서버 로그로 직접 확인한 실제 원인: concurrency=5(풀 크기와
      // 동일)로 반복 실행하면 간헐적으로
      //   "HikariPool-1 - Connection is not available, request timed out after
      //    5000ms (total=5, active=5, idle=0, waiting=0)"
      // 가 발생해 요청 전체가 500으로 실패한다 — 동시성이 풀 크기와 정확히 같으면
      // 여유가 0이라, 타이밍이 조금만 겹쳐도(다른 요청이 잠깐 커넥션을 더 오래 쥐는
      // 순간) 즉시 풀이 바닥난다. concurrency=2~4에서는 반복 실행해도 재현되지 않았다.
      // 이 시나리오는 "잠금 행 경합 자체의 지연"을 재려는 것이므로 풀 여유를 남기는
      // 3으로 낮춘다. "풀 크기와 동시성이 같을 때의 취약함"은 그 자체로 유의미한
      // 발견이라 이 파일 상단에 기록해 둔다(운영 풀 크기 15 기준 재측정 필요).
      name: "saved-numbers-create-contended",
      concurrency: 3,
      durationMs: 5000,
      request: async () => {
        const res = await fetch(`${baseUrl}/api/v1/saved`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Device-Token": pickSavedToken(),
          },
          body: JSON.stringify({ numbers: randomLottoNumbers(), label: null, source: "PERF_TEST" }),
        });
        // 클라이언트당 저장 한도(KRAFT_SAVED_MAX_PER_CLIENT) 초과로 인한 409는
        // "동시성 처리가 의도대로 작동한 결과"이지 인프라 오류가 아니므로 실패로
        // 집계하지 않는다 — 이 시나리오의 관심사는 잠금 행 경합의 지연/처리량이지
        // 한도 도달 여부가 아니다.
        return { ok: res.ok || res.status === 409 };
      },
    },
  ];
}
