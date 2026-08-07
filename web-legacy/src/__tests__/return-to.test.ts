import { beforeEach, describe, expect, it } from "vitest";
import { consumeReturnTo, saveReturnTo } from "@/lib/return-to";

describe("returnTo 저장·소비", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
  });

  it("정상 경로를 저장하고 한 번만 소비한다", () => {
    saveReturnTo("/community/write");

    expect(consumeReturnTo()).toBe("/community/write");
    expect(consumeReturnTo()).toBeNull();
  });

  it("저장된 값이 없으면 null을 반환한다", () => {
    expect(consumeReturnTo()).toBeNull();
  });

  it("슬래시로 시작하지 않는 값은 저장하지 않는다", () => {
    saveReturnTo("community/write");

    expect(consumeReturnTo()).toBeNull();
  });

  it("프로토콜 상대 경로(//evil.com)는 저장하지 않는다", () => {
    saveReturnTo("//evil.com");

    expect(consumeReturnTo()).toBeNull();
  });

  it("저장 검증을 우회해 프로토콜 상대 경로가 들어가 있어도 소비 시 거부한다", () => {
    window.sessionStorage.setItem("kraft-community-return-to", "//evil.com");

    expect(consumeReturnTo()).toBeNull();
  });
});
