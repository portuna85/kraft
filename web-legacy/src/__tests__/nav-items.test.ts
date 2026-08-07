import { describe, expect, it } from "vitest";
import { isCurrent } from "@/lib/nav-items";

describe("isCurrent", () => {
  it("홈은 정확히 '/'일 때만 현재 경로로 취급한다", () => {
    expect(isCurrent("/", "/")).toBe(true);
    expect(isCurrent("/", "/recommend")).toBe(false);
  });

  it("정확히 일치하거나 하위 경로일 때 현재 경로로 취급한다", () => {
    expect(isCurrent("/community", "/community")).toBe(true);
    expect(isCurrent("/community", "/community/posts/1")).toBe(true);
    expect(isCurrent("/community", "/community-archive")).toBe(false);
  });
});
