import { afterEach, describe, expect, it, vi } from "vitest";

import { initOf, jsonResponse, mockFetch, urlOf } from "@/shared/api/test/fetch-mock";

import { analyzeCombination } from "./api";

afterEach(() => {
  vi.unstubAllGlobals();
});

const analysisBody = {
  numbers: [1, 2, 3, 4, 5, 6],
  oddCount: 3,
  evenCount: 3,
  lowCount: 3,
  highCount: 3,
  sumOfNumbers: 21,
  sumBucket: "21-65",
  consecutivePairCount: 0,
  rangeDistribution: [],
  wonFirstPrize: false,
  firstPrizeHistory: [],
};

describe("analyzeCombination", () => {
  it("번호를 본문에 실어 POST하고 no-store로 호출한다", async () => {
    const spy = mockFetch(jsonResponse(analysisBody));

    await analyzeCombination([1, 2, 3, 4, 5, 6]);

    expect(urlOf(spy)).toBe("http://backend:8080/api/v1/stats/analysis");
    const init = initOf(spy) as { method?: string; body?: string; cache?: string; next?: unknown };
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body ?? "{}")).toEqual({ numbers: [1, 2, 3, 4, 5, 6] });
    expect(init.cache).toBe("no-store");
    expect(init.next).toBeUndefined();
  });
});
