import { describe, expect, it } from "vitest";

import { isValidPayload } from "./route";

describe("클라이언트 오류 페이로드 검증", () => {
  it("message와 route가 있으면 통과시킨다", () => {
    expect(isValidPayload({ message: "boom", route: "/" })).toBe(true);
  });

  it("digest는 선택 필드다", () => {
    expect(isValidPayload({ message: "boom", route: "/", digest: "abc123" })).toBe(true);
  });

  it("message나 route가 비어 있으면 막는다", () => {
    expect(isValidPayload({ message: "", route: "/" })).toBe(false);
    expect(isValidPayload({ message: "boom", route: "" })).toBe(false);
  });

  it("객체가 아니면 막는다", () => {
    expect(isValidPayload(null)).toBe(false);
    expect(isValidPayload("boom")).toBe(false);
  });
});
