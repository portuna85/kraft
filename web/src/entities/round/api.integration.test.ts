import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";

import { server } from "../../../tests/msw/server";

import { getLatestRound, getRoundFreshness } from "./api";

/**
 * MSW를 실제로 거치는 통합 테스트 — improvement_fe.md §21.7
 *
 * 다른 entity 테스트는 API 모듈을 `vi.mock`으로 직접 대체하지만, 이 테스트는
 * `serverFetch` → 진짜 fetch → MSW 인터셉트 → Valibot 검증까지 전체 경로를 태운다.
 * MSW 핸들러(tests/msw/handlers.ts)가 실제 엔티티 스키마를 계속 만족하는지 확인하는
 * 유일한 자리다 — 핸들러만 있고 아무도 거치지 않으면 핸들러 자체가 드리프트돼도
 * 아무도 모른다.
 */
describe("round API — MSW 통합", () => {
  beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
  afterEach(() => server.resetHandlers());
  afterAll(() => server.close());

  it("최신 회차를 MSW 핸들러에서 받아 스키마 검증까지 통과한다", async () => {
    const result = await getLatestRound();
    expect(result.round).toBe(1150);
    expect(result.numbers).toHaveLength(6);
  });

  it("데이터 신선도를 MSW 핸들러에서 받는다", async () => {
    const result = await getRoundFreshness();
    expect(result.fresh).toBe(true);
  });
});
