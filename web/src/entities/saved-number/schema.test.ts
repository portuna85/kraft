import * as v from "valibot";
import { describe, expect, it } from "vitest";

import type { components } from "@/generated/api-types";

import {
  isTopPrize,
  isWinning,
  NO_PRIZE_TIER,
  saveNumberResultSchema,
  savedNumberMatchSchema,
  savedNumberSchema,
  type SavedNumber,
} from "./schema";

/** 생성 타입과의 정합성 */
type GeneratedSavedNumber = components["schemas"]["SavedNumberResponse"];
const _typesMatch: SavedNumber extends GeneratedSavedNumber ? true : never = true;
void _typesMatch;

describe("저장 번호 스키마", () => {
  const VALID = {
    id: 1,
    numbers: [1, 8, 17, 24, 33, 41],
    label: null,
    source: "recommend",
    createdAt: "2026-08-01T12:00:00Z",
  };

  it("정상 응답을 통과시킨다", () => {
    expect(v.safeParse(savedNumberSchema, VALID).success).toBe(true);
  });

  it("6개가 아닌 배열을 막는다", () => {
    // 레거시 타입가드는 배열 여부만 확인해 요소 개수를 검사하지 않았다(§6.2 H-3).
    expect(v.safeParse(savedNumberSchema, { ...VALID, numbers: [1, 2, 3] }).success).toBe(false);
  });

  it("범위를 벗어난 번호를 막는다", () => {
    expect(
      v.safeParse(savedNumberSchema, { ...VALID, numbers: [0, 8, 17, 24, 33, 41] }).success,
    ).toBe(false);
    expect(
      v.safeParse(savedNumberSchema, { ...VALID, numbers: [1, 8, 17, 24, 33, 46] }).success,
    ).toBe(false);
  });
});

describe("저장 응답 스키마", () => {
  it("created 필드로 신규·중복을 구분한다", () => {
    const base = {
      id: 1,
      numbers: [1, 2, 3, 4, 5, 6],
      label: null,
      source: "recommend",
      createdAt: "2026-08-01T12:00:00Z",
    };
    expect(v.safeParse(saveNumberResultSchema, { savedNumber: base, created: true }).success).toBe(
      true,
    );
    expect(v.safeParse(saveNumberResultSchema, { savedNumber: base, created: false }).success).toBe(
      true,
    );
  });
});

describe("회차 대조 스키마", () => {
  const VALID = {
    savedNumber: {
      id: 1,
      numbers: [1, 2, 3, 4, 5, 6],
      label: null,
      source: "recommend",
      createdAt: "2026-08-01T12:00:00Z",
    },
    round: 1150,
    drawDate: "2026-08-01",
    drawNumbers: [1, 2, 3, 4, 5, 6],
    bonusNumber: 7,
    matchedCount: 6,
    bonusMatch: false,
    prizeTier: "1등",
  };

  it("정상 응답을 통과시킨다", () => {
    expect(v.safeParse(savedNumberMatchSchema, VALID).success).toBe(true);
  });
});

describe("당첨 여부 판정", () => {
  it("낙첨 문자열만 미당첨으로 본다", () => {
    expect(isWinning(NO_PRIZE_TIER)).toBe(false);
    expect(isWinning("")).toBe(false);
  });

  it("그 외 등수 문자열은 당첨으로 본다", () => {
    expect(isWinning("1등")).toBe(true);
    expect(isWinning("5등")).toBe(true);
  });
});

describe("축하 연출 대상 등수", () => {
  it("1~3등만 축하 대상이다", () => {
    expect(isTopPrize("1등")).toBe(true);
    expect(isTopPrize("2등")).toBe(true);
    expect(isTopPrize("3등")).toBe(true);
  });

  it("4·5등과 낙첨에는 연출을 틀지 않는다", () => {
    expect(isTopPrize("4등")).toBe(false);
    expect(isTopPrize("5등")).toBe(false);
    expect(isTopPrize(NO_PRIZE_TIER)).toBe(false);
    expect(isTopPrize("")).toBe(false);
  });
});
