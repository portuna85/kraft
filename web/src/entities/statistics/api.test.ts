import { afterEach, describe, expect, it, vi } from "vitest";

import { initOf, jsonResponse, mockFetch, urlOf } from "@/shared/api/test/fetch-mock";

import {
  analyzeCombination,
  FREQUENCY_LIMITS,
  getCompanionStats,
  getFrequencyStats,
  getPatternStats,
  REVALIDATE_STATS_SECONDS,
} from "./api";

afterEach(() => {
  vi.unstubAllGlobals();
});

const frequencyBody = {
  totalRounds: 1150,
  frequencies: [],
  topSix: { balls: [], wonFirstPrize: false, firstPrizeHistory: [] },
  bottomSix: { balls: [], wonFirstPrize: false, firstPrizeHistory: [] },
};

const patternBody = { totalRounds: 1150, oddCounts: [], highCounts: [], sumBuckets: [] };
const companionBody = { totalRounds: 1150, topPairs: [] };
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

// 백엔드 StatisticsApiController.ALLOWED_LIMITS와 어긋나면 400 INVALID_LIMIT이
// serverFetch를 거쳐 5xx로 전파된다 — 값 자체를 잠근다.
describe("FREQUENCY_LIMITS", () => {
  it("백엔드가 허용하는 값 집합과 같다", () => {
    expect(FREQUENCY_LIMITS).toEqual([100, 200, 500]);
  });
});

describe("getFrequencyStats", () => {
  it("limit을 안 주면 쿼리 없이 조회한다", async () => {
    const spy = mockFetch(jsonResponse(frequencyBody));

    await getFrequencyStats();

    expect(urlOf(spy)).toBe("http://backend:8080/api/v1/stats/frequency");
  });

  it("limit을 주면 쿼리로 붙인다", async () => {
    const spy = mockFetch(jsonResponse(frequencyBody));

    await getFrequencyStats(200);

    expect(urlOf(spy)).toBe("http://backend:8080/api/v1/stats/frequency?limit=200");
  });

  it("stats:all 태그로 1800초 재검증한다", async () => {
    const spy = mockFetch(jsonResponse(frequencyBody));

    await getFrequencyStats();

    const init = initOf(spy) as { next?: { revalidate: number; tags: string[] } };
    expect(init.next).toEqual({ revalidate: REVALIDATE_STATS_SECONDS, tags: ["stats:all"] });
  });
});

describe("getPatternStats", () => {
  it("파라미터 없이 stats:all 태그로 조회한다", async () => {
    const spy = mockFetch(jsonResponse(patternBody));

    await getPatternStats();

    expect(urlOf(spy)).toBe("http://backend:8080/api/v1/stats/patterns");
    const init = initOf(spy) as { next?: { tags: string[] } };
    expect(init.next?.tags).toEqual(["stats:all"]);
  });
});

describe("getCompanionStats", () => {
  it("ball 없이 조회하면 쿼리가 없다", async () => {
    const spy = mockFetch(jsonResponse(companionBody));

    await getCompanionStats();

    expect(urlOf(spy)).toBe("http://backend:8080/api/v1/stats/companion");
  });

  it("ball을 주면 쿼리로 붙인다", async () => {
    const spy = mockFetch(jsonResponse(companionBody));

    await getCompanionStats(17);

    expect(urlOf(spy)).toBe("http://backend:8080/api/v1/stats/companion?ball=17");
  });
});

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
