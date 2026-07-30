import { describe, expect, it } from "vitest";
import { communitySectionsIdentical } from "@/lib/home-community-sections";

describe("communitySectionsIdentical", () => {
  it("두 목록이 모두 비어 있으면 동일하다고 판단한다", () => {
    expect(communitySectionsIdentical([], [])).toBe(true);
  });

  it("id 집합이 완전히 같으면(순서가 달라도) 동일하다고 판단한다", () => {
    expect(communitySectionsIdentical([{ id: 1 }, { id: 2 }], [{ id: 2 }, { id: 1 }])).toBe(true);
  });

  it("글이 늘어 목록이 달라지면 동일하지 않다고 판단한다", () => {
    expect(communitySectionsIdentical([{ id: 1 }, { id: 2 }], [{ id: 1 }, { id: 3 }])).toBe(false);
  });

  it("길이가 다르면 동일하지 않다고 판단한다", () => {
    expect(communitySectionsIdentical([{ id: 1 }], [{ id: 1 }, { id: 2 }])).toBe(false);
  });
});
