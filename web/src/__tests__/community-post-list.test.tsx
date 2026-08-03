import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { CommunityPostList } from "@/features/community/community-post-list";
import { BlockedUsersProvider } from "@/features/community/blocked-users-context";
import { CommunitySessionProvider } from "@/lib/community-session-provider";
import { vi } from "vitest";

vi.mock("next/navigation", () => ({
  usePathname: () => "/community",
}));

const SESSION = { loggedIn: true, userId: 1, nickname: "나", activeProviders: ["google"] };

const POSTS = [
  {
    id: 1,
    ownerId: 10,
    authorNickname: "작성자A",
    title: "글 A",
    content: "",
    category: "GENERAL" as const,
    status: "PUBLISHED" as const,
    version: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    likeCount: 0,
    commentCount: 0,
    viewCount: 0,
    recommendationAttachment: null,
  },
  {
    id: 2,
    ownerId: 20,
    authorNickname: "작성자B",
    title: "글 B",
    content: "",
    category: "GENERAL" as const,
    status: "PUBLISHED" as const,
    version: 0,
    createdAt: "2026-01-02T00:00:00Z",
    updatedAt: "2026-01-02T00:00:00Z",
    likeCount: 0,
    commentCount: 0,
    viewCount: 0,
    recommendationAttachment: null,
  },
];

function renderList(blockedUserIds: number[]) {
  global.fetch = vi.fn().mockImplementation((url: string) => {
    if (url.includes("/session")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
    }
    if (url.includes("/blocked-users")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(blockedUserIds) });
    }
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(null) });
  });
  return render(
    <CommunitySessionProvider>
      <BlockedUsersProvider>
        <CommunityPostList items={POSTS} />
      </BlockedUsersProvider>
    </CommunitySessionProvider>
  );
}

// C-1: 서버가 내려준 공개 목록(ISR 캐시)은 그대로 두고, 렌더링 시점에만 차단된
// 작성자의 글을 걸러낸다 — UI가 "게시글이 보이지 않습니다"라고 약속한 부분을 지킨다.
describe("커뮤니티 피드의 차단 필터링(C-1)", () => {
  it("차단하지 않았으면 모든 글이 보인다", async () => {
    renderList([]);

    expect(await screen.findByText("글 A")).toBeInTheDocument();
    expect(screen.getByText("글 B")).toBeInTheDocument();
  });

  it("차단한 작성자의 글은 목록에서 사라진다", async () => {
    renderList([10]);

    // 세션·차단 목록 조회는 서로 독립된 fetch라 완료 순서가 보장되지 않는다 — "글 B가
    // 이미 보인다"는 차단 목록 반영 여부와 무관하게 먼저 참일 수 있으므로, 실제로
    // 걸러지는 조건("글 A" 소멸) 자체를 폴링한다.
    await waitFor(() => {
      expect(screen.queryByText("글 A")).not.toBeInTheDocument();
    });
    expect(screen.getByText("글 B")).toBeInTheDocument();
  });
});
