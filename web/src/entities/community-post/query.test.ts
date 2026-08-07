import { describe, expect, it } from "vitest";

import { buildListHref, hasActiveFilter, normalizeQuery, parsePage, parseSort } from "./query";

describe("페이지 파라미터 파싱", () => {
  it("정상 값을 그대로 통과시킨다", () => {
    expect(parsePage("3")).toBe(3);
    expect(parsePage(undefined)).toBe(0);
  });

  it("비정상 값을 0으로 되돌린다 (T-12, M-11)", () => {
    // Number()만 쓰면 Infinity와 1e21이 그대로 백엔드로 나가 400이 된다.
    expect(parsePage("Infinity")).toBe(0);
    expect(parsePage("1.5")).toBe(0);
    expect(parsePage("1e21")).toBe(0);
    expect(parsePage("-3")).toBe(0);
    expect(parsePage("abc")).toBe(0);
    expect(parsePage("")).toBe(0);
  });

  it("상한을 넘는 페이지를 0으로 되돌린다", () => {
    expect(parsePage("100001")).toBe(0);
    expect(parsePage("100000")).toBe(100000);
  });
});

describe("검색어 정규화", () => {
  it("2자 미만이면 백엔드로 보내지 않는다 (T-11, M-11)", () => {
    expect(normalizeQuery("가")).toBeUndefined();
    expect(normalizeQuery(" ")).toBeUndefined();
    expect(normalizeQuery("")).toBeUndefined();
  });

  it("2자 이상은 통과시킨다", () => {
    expect(normalizeQuery("당첨")).toBe("당첨");
  });

  it("앞뒤 공백을 제거한 뒤 길이를 판정한다", () => {
    expect(normalizeQuery("  당첨  ")).toBe("당첨");
    expect(normalizeQuery("  가  ")).toBeUndefined();
  });

  it("50자를 넘으면 잘라낸다", () => {
    expect(normalizeQuery("가".repeat(60))).toHaveLength(50);
  });
});

describe("정렬 파싱", () => {
  it("허용된 값만 받고 나머지는 기본값이다", () => {
    expect(parseSort("weekly_popular")).toBe("weekly_popular");
    expect(parseSort("random")).toBe("latest");
    expect(parseSort(undefined)).toBe("latest");
  });
});

describe("빈 결과 원인 구분", () => {
  it("필터가 걸려 있는지로 판단한다 (FE-048)", () => {
    const base = { page: 0, category: undefined, sort: "latest" as const, query: undefined };

    expect(hasActiveFilter(base)).toBe(false);
    expect(hasActiveFilter({ ...base, query: "당첨" })).toBe(true);
    expect(hasActiveFilter({ ...base, category: "QUESTION" })).toBe(true);
  });
});

describe("목록 링크 생성", () => {
  const base = { page: 0, category: undefined, sort: "latest" as const, query: undefined };

  it("기본값은 URL에서 생략한다", () => {
    expect(buildListHref(base)).toBe("/community");
  });

  it("조건을 유지한 채 일부만 바꾼다", () => {
    const withQuery = { ...base, query: "당첨", page: 2 };
    const href = buildListHref(withQuery, { page: 0 });

    // 페이지만 초기화되고 검색어는 남는다. 값은 URL 인코딩된다.
    expect(href).not.toContain("page=");
    expect(href).toContain(`query=${encodeURIComponent("당첨")}`);
  });

  it("카테고리와 정렬을 함께 싣는다", () => {
    const href = buildListHref({ ...base, category: "QUESTION", sort: "weekly_popular" });
    expect(href).toContain("category=QUESTION");
    expect(href).toContain("sort=weekly_popular");
  });
});
