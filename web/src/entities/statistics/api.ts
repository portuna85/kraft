import { serverFetch } from "@/shared/api/transport";
import { serverEnv } from "@/shared/config/env";

import { frequencyStatsSchema, type FrequencyStats } from "./schema";

/**
 * 통계 API 바인딩 — improvement_fe.md §7.5, §13.5
 * 태그 이름은 백엔드와 일치해야 한다(§24.2 3번).
 */
export const STATS_TAG = "stats:all";
export const REVALIDATE_STATS_SECONDS = 1800;

/**
 * 기간 필터는 **서버 조회**다(§19.2 P-7).
 *
 * 4개 기간을 한 문서에 담아 클라이언트에서 거르면 페이로드가 4배가 된다. 기간이 URL에
 * 실리면 공유·뒤로가기도 자연히 동작한다(§14.2).
 */
export function getFrequencyStats(limit?: number): Promise<FrequencyStats> {
  const query = limit === undefined ? "" : `?limit=${limit}`;
  return serverFetch(
    `${serverEnv.backendInternalUrl}/api/v1/stats/frequency${query}`,
    frequencyStatsSchema,
    {
      cache: { mode: "revalidate", seconds: REVALIDATE_STATS_SECONDS, tags: [STATS_TAG] },
    },
  );
}
