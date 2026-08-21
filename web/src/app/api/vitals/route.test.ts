import { describe, expect, it } from "vitest";

import { isValidPayload } from "./route";

const VALID = {
  name: "LCP",
  value: 1200,
  rating: "good",
  route: "/",
  deviceClass: "mobile",
  layoutClass: "compact",
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

  // RSP-38(docs/improvement.md): deviceClass(640/1024px)와 실제 셸 전환(1152px)이
  // 어긋나 1024~1151px 탭바 UI가 desktop에 섞였다 — layoutClass가 그 상태를
  // 별도로 반영한다.
  it("화이트리스트 밖 layoutClass를 막는다", () => {
    expect(isValidPayload({ ...VALID, layoutClass: "tablet" })).toBe(false);
  });

  it("layoutClass가 없으면 막는다", () => {
    const { layoutClass: _layoutClass, ...withoutLayoutClass } = VALID;
    expect(isValidPayload(withoutLayoutClass)).toBe(false);
  });

  it("desktop-nav layoutClass를 통과시킨다", () => {
    expect(isValidPayload({ ...VALID, layoutClass: "desktop-nav" })).toBe(true);
  });

  it("객체가 아니면 막는다", () => {
    expect(isValidPayload(null)).toBe(false);
    expect(isValidPayload("LCP")).toBe(false);
  });
});
