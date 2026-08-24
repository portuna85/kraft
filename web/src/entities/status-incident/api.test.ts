import { afterEach, describe, expect, it, vi } from "vitest";

import { CACHE_TAGS } from "@/shared/config/cache-tags";
import { initOf, jsonResponse, mockFetch, urlOf } from "@/shared/api/test/fetch-mock";

import { getStatusIncidents, REVALIDATE_INCIDENTS_SECONDS } from "./api";

afterEach(() => {
  vi.unstubAllGlobals();
});

const incidentsBody = [
  {
    round: 1150,
    type: "EXTERNAL_COLLECT",
    resolved: true,
    occurredAt: "2025-01-01T00:00:00Z",
    occurrences: 1,
  },
];

describe("getStatusIncidents", () => {
  it("공개 이력 경로를 조회한다", async () => {
    const spy = mockFetch(jsonResponse(incidentsBody));

    const result = await getStatusIncidents();

    expect(urlOf(spy)).toBe("http://backend:8080/api/v1/status/incidents");
    expect(result).toEqual(incidentsBody);
  });

  it("자기 태그가 아니라 rounds:latest 태그에 얹혀 60초 재검증한다", async () => {
    // 직관과 어긋나는 설계다 — 이력이 늘어나는 계기가 회차 수집이라 회차 태그를 함께
    // 쓴다(status-incident/api.ts 주석). 실수로 자기 태그로 바뀌면 회차가 갱신돼도
    // 이 목록만 stale해진다.
    const spy = mockFetch(jsonResponse(incidentsBody));

    await getStatusIncidents();

    const init = initOf(spy) as { next?: { revalidate: number; tags: string[] } };
    expect(init.next).toEqual({
      revalidate: REVALIDATE_INCIDENTS_SECONDS,
      tags: [CACHE_TAGS.roundsLatest],
    });
  });
});
