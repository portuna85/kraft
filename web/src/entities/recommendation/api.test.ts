import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { DEVICE_TOKEN_STORAGE_KEY } from "@/shared/api/device-token";
import { headersOf, initOf, jsonResponse, mockFetch, urlOf } from "@/shared/api/test/fetch-mock";

import {
  listAccountRecommendationSets,
  listDeviceRecommendationSets,
  recommendNumbers,
  recommendNumbersForAccount,
  type RecommendInput,
} from "./api";

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
  window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "device-abc");
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const input: RecommendInput = {
  strategy: "random",
  count: 1,
  lockedNumbers: [],
  excludedNumbers: [],
};

const recommendBody = {
  recommendations: [[1, 2, 3, 4, 5, 6]],
  strategy: "random",
  algorithmVersion: "v1",
  historyThroughRound: 1150,
  historicalExclusionApplied: false,
  exclusionPolicyVersion: "v1",
  setId: null,
  items: null,
  createdAt: null,
};

const setPageBody = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe("익명 vs 계정 스코프 — 엔드포인트가 다르다", () => {
  it("recommendNumbers(익명)는 /api/v1/numbers/recommend + X-Device-Token을 쓴다", async () => {
    const spy = mockFetch(jsonResponse(recommendBody));

    await recommendNumbers(input);

    expect(urlOf(spy)).toBe("/api/v1/numbers/recommend");
    expect(headersOf(spy)["X-Device-Token"]).toBe("device-abc");
  });

  it("recommendNumbersForAccount(로그인)는 다른 경로를 쓰고 디바이스 토큰을 안 붙인다", async () => {
    const spy = mockFetch(jsonResponse(recommendBody));

    await recommendNumbersForAccount(input);

    expect(urlOf(spy)).toBe("/api/v1/community/me/recommendation-sets");
    expect(headersOf(spy)["X-Device-Token"]).toBeUndefined();
  });

  it("listDeviceRecommendationSets는 device 경로 + 토큰을 쓴다", async () => {
    const spy = mockFetch(jsonResponse(setPageBody));

    await listDeviceRecommendationSets(1);

    expect(urlOf(spy)).toBe("/api/v1/recommendation-sets?page=1&size=20");
    expect(headersOf(spy)["X-Device-Token"]).toBe("device-abc");
  });

  it("listAccountRecommendationSets는 account 경로를 쓰고 토큰이 없다", async () => {
    const spy = mockFetch(jsonResponse(setPageBody));

    await listAccountRecommendationSets(1);

    expect(urlOf(spy)).toBe("/api/v1/community/me/recommendation-sets?page=1&size=20");
    expect(headersOf(spy)["X-Device-Token"]).toBeUndefined();
  });
});

describe("recommendNumbers 본문", () => {
  it("전략·개수·고정/제외 번호를 그대로 JSON으로 싣는다", async () => {
    const spy = mockFetch(jsonResponse(recommendBody));

    await recommendNumbers({
      strategy: "balanced",
      count: 3,
      lockedNumbers: [7],
      excludedNumbers: [13, 21],
    });

    const init = initOf(spy) as { body?: string };
    expect(JSON.parse(init.body ?? "{}")).toEqual({
      strategy: "balanced",
      count: 3,
      lockedNumbers: [7],
      excludedNumbers: [13, 21],
    });
  });

  it("CSRF 쿠키가 없으면 요청을 보내지 않는다", async () => {
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
    const spy = mockFetch(jsonResponse(recommendBody));

    await expect(recommendNumbers(input)).rejects.toMatchObject({ code: "CSRF_TOKEN_MISSING" });
    expect(spy).not.toHaveBeenCalled();
  });
});
