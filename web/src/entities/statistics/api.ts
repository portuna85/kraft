import { serverFetch } from "@/shared/api/transport";
import { serverEnv } from "@/shared/config/env";

import {
  companionStatsSchema,
  frequencyStatsSchema,
  patternStatsSchema,
  type CompanionStats,
  type FrequencyStats,
  type PatternStats,
} from "./schema";

/**
 * 통계 API 바인딩 — improvement_fe.md §7.5, §13.5
 * 태그 이름은 백엔드와 일치해야 한다(§24.2 3번).
 */
export const STATS_TAG = "stats:all";
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
