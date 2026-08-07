import { describe, expect, it } from "vitest";
import { validateLottoNumbers, parseExcludedNumbers } from "@/lib/lotto-validation";

describe("로또 번호 검증", () => {
  it("유효한 6개 숫자 세트를 허용한다", () => {
    const result = validateLottoNumbers([1, 7, 15, 23, 38, 45]);
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.numbers).toEqual([1, 7, 15, 23, 38, 45]);
  });

  it("6개보다 적은 숫자를 거부한다", () => {
    const result = validateLottoNumbers([1, 2, 3, 4, 5]);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.message).toMatch(/6개/);
  });

  it("6개보다 많은 숫자를 거부한다", () => {
    const result = validateLottoNumbers([1, 2, 3, 4, 5, 6, 7]);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.message).toMatch(/6개/);
  });

  it("1 미만인 숫자를 거부한다", () => {
    const result = validateLottoNumbers([0, 2, 3, 4, 5, 6]);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.message).toMatch(/1에서 45/);
  });

  it("45 초과인 숫자를 거부한다", () => {
    const result = validateLottoNumbers([1, 2, 3, 4, 5, 46]);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.message).toMatch(/1에서 45/);
  });

  it("중복된 숫자를 거부한다", () => {
    const result = validateLottoNumbers([1, 2, 3, 4, 5, 5]);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.message).toMatch(/중복/);
  });

  it("경계값 1과 45를 허용한다", () => {
    const result = validateLottoNumbers([1, 2, 3, 4, 5, 45]);
    expect(result.ok).toBe(true);
  });

  it("문자열로 인코딩된 숫자를 허용한다", () => {
    const result = validateLottoNumbers(["1", "7", "15", "23", "38", "45"]);
    expect(result.ok).toBe(true);
  });
});

describe("제외 번호 파싱", () => {
  it("쉼표로 구분된 유효한 숫자 목록을 파싱한다", () => {
    const { valid, ignored } = parseExcludedNumbers("1, 10, 45");
    expect(valid).toEqual([1, 10, 45]);
    expect(ignored).toEqual([]);
  });

  it("범위를 벗어난 값을 무시 목록에 담는다", () => {
    const { valid, ignored } = parseExcludedNumbers("0, 5, 46");
    expect(valid).toEqual([5]);
    expect(ignored).toEqual(["0", "46"]);
  });

  it("숫자가 아닌 토큰을 무시 목록에 담는다", () => {
    const { valid, ignored } = parseExcludedNumbers("3, abc, 7");
    expect(valid).toEqual([3, 7]);
    expect(ignored).toEqual(["abc"]);
  });

  it("빈 문자열을 처리한다", () => {
    const { valid, ignored } = parseExcludedNumbers("");
    expect(valid).toEqual([]);
    expect(ignored).toEqual([]);
  });

  it("공백만 있는 문자열을 처리한다", () => {
    const { valid, ignored } = parseExcludedNumbers("   ,  , ");
    expect(valid).toEqual([]);
    expect(ignored).toEqual([]);
  });

  it("경계값 1과 45를 허용한다", () => {
    const { valid, ignored } = parseExcludedNumbers("1, 45");
    expect(valid).toEqual([1, 45]);
    expect(ignored).toEqual([]);
  });

  it("중복된 값을 제거한다", () => {
    const { valid, ignored } = parseExcludedNumbers("3, 3, 5");
    expect(valid).toEqual([3, 5]);
    expect(ignored).toEqual([]);
  });
});
