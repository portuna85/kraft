import { describe, expect, it } from "vitest";

import { isCompleteCombination, parseCombinationInput } from "./combination";

describe("조합 입력 파싱", () => {
  it("쉼표와 공백을 모두 구분자로 받는다", () => {
    expect(parseCombinationInput("1, 8, 17, 24, 33, 41")).toEqual([1, 8, 17, 24, 33, 41]);
    expect(parseCombinationInput("1 8 17 24 33 41")).toEqual([1, 8, 17, 24, 33, 41]);
    expect(parseCombinationInput("1,8,17,24,33,41,")).toEqual([1, 8, 17, 24, 33, 41]);
  });

  it("범위를 벗어난 값과 숫자가 아닌 조각을 버린다", () => {
    // URL을 손으로 고친 요청이 백엔드 400이 아니라 화면 안내로 끝나야 한다.
    expect(parseCombinationInput("0, 46, abc, 7")).toEqual([7]);
    expect(parseCombinationInput("")).toEqual([]);
  });

  it("중복은 남긴다 — 사용자에게 알려야 할 입력 오류이기 때문이다", () => {
    expect(parseCombinationInput("7, 7")).toEqual([7, 7]);
    expect(isCompleteCombination([7, 7, 1, 2, 3, 4])).toBe(false);
  });

  it("6개를 넘으면 앞에서 6개만 취한다", () => {
    expect(parseCombinationInput("1,2,3,4,5,6,7,8")).toEqual([1, 2, 3, 4, 5, 6]);
  });
});

describe("완성된 조합 판정", () => {
  it("서로 다른 6개가 1~45 안에 있어야 통과한다", () => {
    expect(isCompleteCombination([1, 8, 17, 24, 33, 41])).toBe(true);
  });

  it("개수가 모자라거나 넘치면 막는다", () => {
    expect(isCompleteCombination([1, 2, 3, 4, 5])).toBe(false);
    expect(isCompleteCombination([1, 2, 3, 4, 5, 6, 7])).toBe(false);
  });

  it("범위를 벗어난 번호를 막는다", () => {
    expect(isCompleteCombination([0, 2, 3, 4, 5, 6])).toBe(false);
    expect(isCompleteCombination([1, 2, 3, 4, 5, 46])).toBe(false);
  });
});
