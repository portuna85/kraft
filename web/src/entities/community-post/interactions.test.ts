import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { getMyInteractions, MAX_INTERACTION_POST_IDS } from "./interactions";

describe("내 상호작용 조회", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("postIds가 비면 요청 자체를 보내지 않는다", async () => {
    // 빈 배열을 보내면 쿼리 파라미터가 사라져 @RequestParam이 없는 요청이 되고
    // 백엔드가 400을 낸다(레거시 C-1).
    const result = await getMyInteractions([]);

    expect(fetch).not.toHaveBeenCalled();
    expect(result).toEqual({ likedPostIds: [], bookmarkedPostIds: [], blockedUserIds: [] });
  });

  it("백엔드 상한을 넘는 id는 잘라서 보낸다", async () => {
    const spy = vi
      .mocked(fetch)
      .mockResolvedValue(
        new Response(
          JSON.stringify({ likedPostIds: [], bookmarkedPostIds: [], blockedUserIds: [] }),
          { status: 200 },
        ),
      );

    const tooMany = Array.from({ length: MAX_INTERACTION_POST_IDS + 20 }, (_, i) => i + 1);
    await getMyInteractions(tooMany);

    const url = String(spy.mock.calls[0]?.[0]);
    const ids = url.split("postIds=")[1]?.split(",") ?? [];
    expect(ids).toHaveLength(MAX_INTERACTION_POST_IDS);
  });
});
