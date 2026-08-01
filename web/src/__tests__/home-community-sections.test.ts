import { describe, expect, it } from "vitest";
import { communityPopularPostsAlreadyVisible } from "@/lib/home-community-sections";

describe("communityPopularPostsAlreadyVisible", () => {
  it("인기 글이 비어 있으면 중복으로 판단하지 않는다", () => {
    expect(communityPopularPostsAlreadyVisible([], [])).toBe(false);
  });

  it("화면에 표시할 인기 글이 최신 글 표시 범위에 모두 있으면 중복으로 판단한다", () => {
    expect(communityPopularPostsAlreadyVisible(
      [{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }],
      [{ id: 3 }, { id: 1 }, { id: 2 }],
    )).toBe(true);
  });

  it("인기 글이 최신 글의 표시 범위를 벗어나면 숨기지 않는다", () => {
    expect(communityPopularPostsAlreadyVisible(
      [{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }, { id: 5 }],
      [{ id: 5 }, { id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }],
    )).toBe(false);
  });

  it("표시할 인기 글 중 하나라도 최신 글에 없으면 중복으로 판단하지 않는다", () => {
    expect(communityPopularPostsAlreadyVisible(
      [{ id: 1 }, { id: 2 }],
      [{ id: 1 }, { id: 3 }],
    )).toBe(false);
  });
});
