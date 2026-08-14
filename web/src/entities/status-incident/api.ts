import { serverFetch } from "@/shared/api/transport";
import { CACHE_TAGS } from "@/shared/config/cache-tags";
import { serverEnv } from "@/shared/config/env";

import { statusIncidentListSchema, type StatusIncident } from "./schema";

/**
 * 공개 이력 API
 *
 * 회차 태그(`rounds:latest`)를 함께 단다. 이력이 늘어나는 계기가 곧 회차 수집·보정이라,
 * 회차가 바뀌면 이 목록도 새로 읽어야 한다. 60초는 상태 페이지에 적당한 신선도다.
 */
export const REVALIDATE_INCIDENTS_SECONDS = 60;

export function getStatusIncidents(): Promise<StatusIncident[]> {
  return serverFetch(
    `${serverEnv.backendInternalUrl}/api/v1/status/incidents`,
    statusIncidentListSchema,
    {
      cache: {
        mode: "revalidate",
        seconds: REVALIDATE_INCIDENTS_SECONDS,
        tags: [CACHE_TAGS.roundsLatest],
      },
    },
  );
}
