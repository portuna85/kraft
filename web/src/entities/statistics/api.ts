import { serverFetch } from "@/shared/api/transport";
import { CACHE_TAGS } from "@/shared/config/cache-tags";
import { serverEnv } from "@/shared/config/env";

import {
  analysisSchema,
  companionStatsSchema,
  frequencyStatsSchema,
  patternStatsSchema,
  type CombinationAnalysis,
  type CompanionStats,
  type FrequencyStats,
  type PatternStats,
} from "./schema";

/**
 * 통계 API 바인딩
 * 태그 이름은 백엔드와 일치해야 한다(§24.2 3번).
 */
export const STATS_TAG = CACHE_TAGS.statsAll;
export const REVALIDATE_STATS_SECONDS = 1800;

/**
 * 백엔드가 허용하는 limit 집합 — StatisticsApiController.ALLOWED_LIMITS와 일치해야 한다.
 *
 * 그 밖의 값은 400 INVALID_LIMIT이고, serverFetch가 던져 페이지가 5xx가 된다. 화면이
 * 직접 숫자를 고르면 이 계약이 눈에 안 보이므로 여기서 타입으로 좁힌다.
 */
export const FREQUENCY_LIMITS = [100, 200, 500] as const;

export type FrequencyLimit = (typeof FREQUENCY_LIMITS)[number];

/**
 * 기간 필터는 **서버 조회**다(§19.2 P-7).
 *
 * 4개 기간을 한 문서에 담아 클라이언트에서 거르면 페이로드가 4배가 된다. 기간이 URL에
 * 실리면 공유·뒤로가기도 자연히 동작한다(§14.2).
 */
export function getFrequencyStats(limit?: FrequencyLimit): Promise<FrequencyStats> {
  const query = limit === undefined ? "" : `?limit=${limit}`;
  return serverFetch(
    `${serverEnv.backendInternalUrl}/api/v1/stats/frequency${query}`,
    frequencyStatsSchema,
    {
      cache: { mode: "revalidate", seconds: REVALIDATE_STATS_SECONDS, tags: [STATS_TAG] },
    },
  );
}

/** 홀짝·고저·합계 구간 분포. 파라미터가 없어 전 사용자가 같은 캐시 항목을 공유한다. */
export function getPatternStats(): Promise<PatternStats> {
  return serverFetch(`${serverEnv.backendInternalUrl}/api/v1/stats/patterns`, patternStatsSchema, {
    cache: { mode: "revalidate", seconds: REVALIDATE_STATS_SECONDS, tags: [STATS_TAG] },
  });
}

/**
 * 동반 출현 — ball을 주면 그 번호가 낀 쌍만 서버가 골라 준다.
 *
 * 필터를 서버가 하는 이유는 정확도다(§23.5 불변식). 화면은 상위 50쌍만 받는데,
 * 클라이언트에서 거르면 51위 밖에 있는 쌍은 애초에 손에 없어서 필터 결과가 조용히
 * 불완전해진다. ball 파라미터는 그 번호의 전체 쌍(최대 44개)을 정확히 가져온다.
 */
export function getCompanionStats(ball?: number): Promise<CompanionStats> {
  const query = ball === undefined ? "" : `?ball=${ball}`;
  return serverFetch(
    `${serverEnv.backendInternalUrl}/api/v1/stats/companion${query}`,
    companionStatsSchema,
    {
      cache: { mode: "revalidate", seconds: REVALIDATE_STATS_SECONDS, tags: [STATS_TAG] },
    },
  );
}

/**
 * 조합 분석 — 백엔드가 POST로 받는다(`POST /api/v1/stats/analysis`).
 *
 * 부수효과는 없지만 번호 6개를 본문에 실어야 해서 POST다. Next의 fetch 캐시는 GET에만
 * 걸리므로 no-store로 둔다 — revalidate를 적어 두면 캐시된 줄 알았는데 아닌 상태가 된다.
 *
 * 이 응답은 패턴 분석과 역대 1등 이력을 함께 준다. 그래서 `/api/v1/numbers/check`를
 * 따로 부르지 않아도 "이 조합이 1등으로 나온 적 있는지"까지 한 번에 답할 수 있다.
 */
export function analyzeCombination(numbers: readonly number[]): Promise<CombinationAnalysis> {
  return serverFetch(`${serverEnv.backendInternalUrl}/api/v1/stats/analysis`, analysisSchema, {
    method: "POST",
    body: { numbers: [...numbers] },
    cache: { mode: "no-store" },
  });
}
