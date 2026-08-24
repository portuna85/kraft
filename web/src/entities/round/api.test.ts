import { afterEach, describe, expect, it, vi } from "vitest";

import { CACHE_TAGS } from "@/shared/config/cache-tags";
import { headersOf, initOf, jsonResponse, mockFetch, urlOf } from "@/shared/api/test/fetch-mock";

import { getLatestRound, getRoundFreshness, REVALIDATE_LATEST_SECONDS, ROUNDS_LATEST_TAG } from "./api";

afterEach(() => {
  vi.unstubAllGlobals();
});

const winningNumberBody = {
  round: 1150,
  drawDate: "2025-01-04",
  numbers: [1, 2, 3, 4, 5, 6],
  bonusNumber: 7,
  firstPrizeAmount: 1_000_000_000,
  secondPrize: 0,
  secondWinners: 0,
  totalSales: 0,
  firstAccumAmount: 0,
};

const freshnessBody = {
  latestRound: 1150,
  latestDrawDate: "2025-01-04",
  fresh: true,
  checkedAt: "2025-01-05T00:00:00Z",
};

// 이 두 상수가 CACHE_TAGS/RevalidateWebhookListener와 어긋나면 회차가 조용히
// stale해진다(§7.5) — 값 자체를 여기서도 잠근다.
describe("모듈 상수", () => {
  it("회차 태그는 CACHE_TAGS.roundsLatest를 그대로 쓴다", () => {
    expect(ROUNDS_LATEST_TAG).toBe(CACHE_TAGS.roundsLatest);
  });

  it("재검증 주기는 60초다", () => {
    expect(REVALIDATE_LATEST_SECONDS).toBe(60);
  });
});

describe("getLatestRound", () => {
  it("백엔드 내부 URL로 최신 회차를 조회한다", async () => {
    const spy = mockFetch(jsonResponse(winningNumberBody));

    const result = await getLatestRound();

    expect(urlOf(spy)).toBe("http://backend:8080/api/v1/rounds/latest");
    expect(result).toEqual(winningNumberBody);
  });

  it("rounds:latest 태그로 60초 재검증한다", async () => {
    const spy = mockFetch(jsonResponse(winningNumberBody));

    await getLatestRound();

    const init = initOf(spy) as { next?: { revalidate: number; tags: string[] } };
    expect(init.next).toEqual({ revalidate: 60, tags: ["rounds:latest"] });
  });

  it("스키마와 어긋난 응답은 통과시키지 않는다", async () => {
    // numbers가 5개뿐이면 화면이 조합을 잘못 그린다 — 손으로 쓴 타입가드였다면
    // 놓쳤을 경우다.
    mockFetch(jsonResponse({ ...winningNumberBody, numbers: [1, 2, 3, 4, 5] }));

    await expect(getLatestRound()).rejects.toMatchObject({
      kind: "server",
      code: "SCHEMA_MISMATCH",
    });
  });

  it("백엔드 오류를 그대로 노출한다(200으로 위장하지 않는다)", async () => {
    mockFetch(jsonResponse({ code: "ROUND_NOT_FOUND", message: "없는 회차입니다." }, 404));

    const error = await getLatestRound().catch((e: unknown) => e);

    expect(error).toMatchObject({ kind: "client", code: "ROUND_NOT_FOUND", status: 404 });
  });
});

describe("getRoundFreshness", () => {
  it("freshness 경로를 rounds:latest 태그로 60초 재검증한다", async () => {
    const spy = mockFetch(jsonResponse(freshnessBody));

    const result = await getRoundFreshness();

    expect(urlOf(spy)).toBe("http://backend:8080/api/v1/rounds/freshness");
    const init = initOf(spy) as { next?: { revalidate: number; tags: string[] } };
    expect(init.next).toEqual({ revalidate: 60, tags: ["rounds:latest"] });
    expect(result).toEqual(freshnessBody);
  });
});

describe("헤더", () => {
  it("GET 요청은 accept만 보내고 content-type을 붙이지 않는다", async () => {
    const spy = mockFetch(jsonResponse(winningNumberBody));

    await getLatestRound();

    expect(headersOf(spy)["content-type"]).toBeUndefined();
    expect(headersOf(spy)["accept"]).toBe("application/json");
  });
});
