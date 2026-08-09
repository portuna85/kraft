import * as v from "valibot";
import { describe, expect, it } from "vitest";

import { recommendationSetPageSchema, recommendationSetSchema } from "./schema";

const VALID_SET = {
  id: 1,
  strategy: "balanced",
  algorithmVersion: "v1",
  historyThroughRound: 1150,
  exclusionPolicyVersion: "v1",
  lockedNumbers: [7],
  excludedNumbers: [13],
  createdAt: "2026-08-01T12:00:00Z",
  items: [
    {
      position: 0,
      numbers: [1, 8, 17, 24, 33, 41],
      score: null,
      explanationCodes: ["ODD_EVEN_BALANCED"],
    },
  ],
};

describe("추천 세트 스키마", () => {
  it("정상 응답을 통과시킨다", () => {
    expect(v.safeParse(recommendationSetSchema, VALID_SET).success).toBe(true);
  });

  it("알 수 없는 설명 코드를 막는다", () => {
    // 코드 → 한국어 문구 매핑이 없는 값이 들어오면 화면에서 빈 문구가 나온다.
    const result = v.safeParse(recommendationSetSchema, {
      ...VALID_SET,
      items: [{ ...VALID_SET.items[0], explanationCodes: ["UNKNOWN_CODE"] }],
    });
    expect(result.success).toBe(false);
  });

  it("조합이 6개가 아니면 막는다", () => {
    const result = v.safeParse(recommendationSetSchema, {
      ...VALID_SET,
      items: [{ ...VALID_SET.items[0], numbers: [1, 2, 3] }],
    });
    expect(result.success).toBe(false);
  });
});

describe("추천 세트 페이지 스키마", () => {
  it("정상 페이지 응답을 통과시킨다", () => {
    const page = { items: [VALID_SET], page: 0, size: 20, totalElements: 1, totalPages: 1 };
    expect(v.safeParse(recommendationSetPageSchema, page).success).toBe(true);
  });
});
