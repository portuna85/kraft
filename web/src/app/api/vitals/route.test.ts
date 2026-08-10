import { describe, expect, it } from "vitest";

import { isValidPayload } from "./route";

const VALID = {
  name: "LCP",
  value: 1200,
  rating: "good",
  route: "/",
  deviceClass: "mobile",
  release: "v1",
};

describe("vitals 페이로드 검증", () => {
  it("정상 페이로드를 통과시킨다", () => {
    expect(isValidPayload(VALID)).toBe(true);
  });

  it("화이트리스트 밖 지표 이름을 막는다", () => {
    expect(isValidPayload({ ...VALID, name: "FID" })).toBe(false);
  });

  it("화이트리스트 밖 등급을 막는다", () => {
    expect(isValidPayload({ ...VALID, rating: "bad" })).toBe(false);
  });

  it("음수·비유한 값을 막는다", () => {
    expect(isValidPayload({ ...VALID, value: -1 })).toBe(false);
    expect(isValidPayload({ ...VALID, value: Infinity })).toBe(false);
  });

  it("route가 비어 있으면 막는다", () => {
    expect(isValidPayload({ ...VALID, route: "" })).toBe(false);
  });

  it("객체가 아니면 막는다", () => {
    expect(isValidPayload(null)).toBe(false);
    expect(isValidPayload("LCP")).toBe(false);
  });
});
