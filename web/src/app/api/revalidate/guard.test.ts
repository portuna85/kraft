import { describe, expect, it } from "vitest";

import { filterAllowedPaths, filterAllowedTags, matchesSecret } from "./guard";

describe("재검증 시크릿 검증", () => {
  it("일치하는 시크릿을 통과시킨다", () => {
    expect(matchesSecret("s3cret", "s3cret")).toBe(true);
  });

  it("불일치하는 시크릿을 막는다", () => {
    expect(matchesSecret("wrong", "s3cret")).toBe(false);
  });

  it("길이가 다른 시크릿을 막는다", () => {
    // timingSafeEqual은 길이가 다르면 던진다 — 길이 검사를 먼저 해야 한다.
    expect(matchesSecret("short", "much-longer-secret")).toBe(false);
  });

  it("헤더가 없으면 막는다", () => {
    expect(matchesSecret(null, "s3cret")).toBe(false);
  });

  it("서버에 시크릿이 설정되지 않았으면 막는다", () => {
    // 미설정을 '제한 없음'으로 보지 않는다 — fail-closed다.
    expect(matchesSecret("anything", undefined)).toBe(false);
    expect(matchesSecret("anything", "")).toBe(false);
  });
});

describe("경로 화이트리스트", () => {
  it("허용된 경로만 통과시킨다", () => {
    expect(filterAllowedPaths(["/", "/frequency", "/unknown"])).toEqual(["/", "/frequency"]);
  });

  it("배열이 아니면 빈 배열을 돌려준다", () => {
    expect(filterAllowedPaths(undefined)).toEqual([]);
    expect(filterAllowedPaths("not-an-array")).toEqual([]);
  });

  it("임의 경로를 재검증하도록 만들 수 없다", () => {
    // 시크릿이 노출되더라도 이 화이트리스트가 마지막 방어선이다.
    expect(filterAllowedPaths(["/community/posts/1", "/../etc/passwd"])).toEqual([]);
  });
});

describe("태그 화이트리스트", () => {
  it("허용된 태그만 통과시킨다", () => {
    expect(filterAllowedTags(["rounds:latest", "stats:all", "community:posts"])).toEqual([
      "rounds:latest",
      "stats:all",
    ]);
  });

  it("community:posts는 백엔드가 절대 보내지 않으므로 화이트리스트에 없다", () => {
    expect(filterAllowedTags(["community:posts"])).toEqual([]);
  });
});
