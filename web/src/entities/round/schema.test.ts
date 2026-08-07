import * as v from "valibot";
import { describe, expect, it } from "vitest";

import { winningNumberSchema } from "./schema";

const VALID = {
  round: 1150,
  drawDate: "2026-08-01",
  numbers: [3, 11, 24, 30, 38, 44],
  bonusNumber: 7,
  firstPrizeAmount: 2_100_000_000,
  secondPrize: 60_000_000,
  secondWinners: 35,
  totalSales: 110_000_000_000,
  firstAccumAmount: 2_100_000_000,
};

describe("당첨 회차 스키마", () => {
  it("정상 응답을 통과시킨다", () => {
    expect(v.safeParse(winningNumberSchema, VALID).success).toBe(true);
  });

  it("번호가 6개가 아니면 막는다", () => {
    // 손으로 쓴 타입가드는 Array.isArray에서 멈춰 이런 응답을 통과시켰다(H-3).
    const result = v.safeParse(winningNumberSchema, { ...VALID, numbers: [3, 11, 24, 30, 38] });
    expect(result.success).toBe(false);
  });

  it("번호 요소가 숫자가 아니면 막는다", () => {
    const result = v.safeParse(winningNumberSchema, {
      ...VALID,
      numbers: [3, 11, 24, 30, 38, "44"],
    });
    expect(result.success).toBe(false);
  });

  it("1~45 범위를 벗어난 번호를 막는다", () => {
    expect(
      v.safeParse(winningNumberSchema, { ...VALID, numbers: [0, 11, 24, 30, 38, 44] }).success,
    ).toBe(false);
    expect(
      v.safeParse(winningNumberSchema, { ...VALID, numbers: [3, 11, 24, 30, 38, 46] }).success,
    ).toBe(false);
  });

  it("정수가 아닌 번호를 막는다", () => {
    const result = v.safeParse(winningNumberSchema, {
      ...VALID,
      numbers: [3.5, 11, 24, 30, 38, 44],
    });
    expect(result.success).toBe(false);
  });

  it("보너스 번호에도 같은 범위 규칙을 적용한다", () => {
    expect(v.safeParse(winningNumberSchema, { ...VALID, bonusNumber: 46 }).success).toBe(false);
  });

  it("필수 필드가 빠지면 막는다", () => {
    const { bonusNumber: _omitted, ...withoutBonus } = VALID;
    expect(v.safeParse(winningNumberSchema, withoutBonus).success).toBe(false);
  });
});
