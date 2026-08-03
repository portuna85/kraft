import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { BlockedPostGate } from "@/features/community/blocked-post-gate";
import { BlockedUsersProvider } from "@/features/community/blocked-users-context";
import { CommunitySessionProvider } from "@/lib/community-session-provider";

vi.mock("next/navigation", () => ({
  usePathname: () => "/community/posts/1",
}));

const SESSION = { loggedIn: true, userId: 1, nickname: "나", activeProviders: ["google"] };

function renderGate(ownerId: number, blockedUserIds: number[]) {
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
        <BlockedPostGate ownerId={ownerId}>
          <p>본문 내용</p>
        </BlockedPostGate>
      </BlockedUsersProvider>
    </CommunitySessionProvider>
  );
}

// C-1: URL 직접 접근 시 완전히 숨기면(404 등) 아무 설명 없는 빈 화면만 보여 혼란스럽다 —
// 대체 상태 + 해제 경로를 제공하는지 검증한다.
describe("게시글 상세의 차단 게이트(C-1)", () => {
  it("차단하지 않은 작성자의 글은 본문을 그대로 보여준다", async () => {
    renderGate(10, []);

    expect(await screen.findByText("본문 내용")).toBeInTheDocument();
    expect(screen.queryByText(/차단한 사용자의 게시글입니다/)).not.toBeInTheDocument();
  });

  it("차단한 작성자의 글은 본문 대신 안내와 해제 버튼을 보여준다", async () => {
    renderGate(10, [10]);

    expect(await screen.findByText(/차단한 사용자가 작성했습니다/)).toBeInTheDocument();
    expect(screen.queryByText("본문 내용")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "차단 해제" })).toBeInTheDocument();
  });
});
