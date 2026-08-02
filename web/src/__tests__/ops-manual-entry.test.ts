import { describe, expect, it } from "vitest";
import { validateManualEntry } from "@/lib/ops-manual-entry";
import { initialManualEntryForm } from "@/lib/ops-types";

const VALID = {
  round: "1230",
  drawDate: "2026-01-03",
  numbers: "1, 2, 3, 4, 5, 6",
  bonusNumber: "7",
  firstPrizeAmount: "2100000000",
};

// FE-078: 폼에서 분리된 순수 함수라 렌더 없이 규칙만 검증한다.
// FE-074: 이 규칙이 없던 시절에는 빈 회차가 0으로, 번호 3개가 그대로 서버에 갔다.
describe("운영 수동 적재 입력 검증", () => {
  it("유효한 입력은 서버에 보낼 본문으로 변환한다", () => {
    const result = validateManualEntry(VALID);

    expect(result).toEqual({
      ok: true,
      body: {
        round: 1230,
        drawDate: "2026-01-03",
        numbers: [1, 2, 3, 4, 5, 6],
        bonusNumber: 7,
        firstPrizeAmount: 2100000000,
      },
    });
  });

  it("빈 폼은 회차부터 막는다", () => {
    const result = validateManualEntry(initialManualEntryForm);

    expect(result).toMatchObject({ ok: false, field: "round" });
  });

  it("회차 0은 거부한다 — Number(\"\")===0이 그대로 전송되던 경로다", () => {
    expect(validateManualEntry({ ...VALID, round: "0" })).toMatchObject({ ok: false, field: "round" });
  });

  it("추첨일이 없으면 거부한다", () => {
    expect(validateManualEntry({ ...VALID, drawDate: "" })).toMatchObject({ ok: false, field: "drawDate" });
  });

  it("번호가 6개가 아니면 거부한다", () => {
    expect(validateManualEntry({ ...VALID, numbers: "1, 2, 3" })).toMatchObject({ ok: false, field: "numbers" });
  });

  it("숫자가 아닌 토큰을 개수에서 빼지 않는다", () => {
    // "3, 어, 19, 28, 34, 42"는 6개지만 하나가 정수가 아니다. NaN을 걸러 5개로
    // 만들어 버리면 "6개여야 합니다"라는 엉뚱한 이유가 보고된다.
    const result = validateManualEntry({ ...VALID, numbers: "3, 어, 19, 28, 34, 42" });

    expect(result).toMatchObject({ ok: false, field: "numbers" });
    if (!result.ok) expect(result.message).toContain("정수");
  });

  it("번호에 중복이 있으면 거부한다", () => {
    expect(validateManualEntry({ ...VALID, numbers: "1, 1, 3, 4, 5, 6" })).toMatchObject({
      ok: false,
      field: "numbers",
    });
  });

  it("범위를 벗어난 번호를 거부한다", () => {
    expect(validateManualEntry({ ...VALID, numbers: "0, 2, 3, 4, 5, 6" })).toMatchObject({
      ok: false,
      field: "numbers",
    });
    expect(validateManualEntry({ ...VALID, numbers: "46, 2, 3, 4, 5, 6" })).toMatchObject({
      ok: false,
      field: "numbers",
    });
  });

  it("보너스 번호가 당첨 번호와 겹치면 거부한다", () => {
    expect(validateManualEntry({ ...VALID, bonusNumber: "6" })).toMatchObject({
      ok: false,
      field: "bonusNumber",
    });
  });

  it("보너스 번호 범위를 검사한다", () => {
    expect(validateManualEntry({ ...VALID, bonusNumber: "46" })).toMatchObject({ ok: false, field: "bonusNumber" });
    expect(validateManualEntry({ ...VALID, bonusNumber: "0" })).toMatchObject({ ok: false, field: "bonusNumber" });
  });

  it("음수 당첨금을 거부한다", () => {
    expect(validateManualEntry({ ...VALID, firstPrizeAmount: "-1" })).toMatchObject({
      ok: false,
      field: "firstPrizeAmount",
    });
  });

  it("쉼표 없이 공백으로만 구분해도 받는다", () => {
    const result = validateManualEntry({ ...VALID, numbers: "1 2 3 4 5 6" });

    expect(result.ok).toBe(true);
  });
});
