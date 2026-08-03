import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { BlockButton } from "@/features/community/block-button";
import { CommunitySessionProvider } from "@/lib/community-session-provider";
import { BlockedUsersProvider } from "@/features/community/blocked-users-context";

vi.mock("next/navigation", () => ({
  usePathname: () => "/community/posts/1",
}));

const SESSION = { loggedIn: true, userId: 1, nickname: "나", activeProviders: ["google"] };

function mockFetch(onWrite?: (url: string, init?: RequestInit) => { status: number; body: unknown }) {
  return vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    if (url.includes("/session")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
    }
    if (url.includes("/blocked-users")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
    }
    const { status, body } = onWrite?.(url, init) ?? { status: 204, body: null };
    return Promise.resolve({
      ok: status >= 200 && status < 300,
      status,
      json: () => Promise.resolve(body),
    });
  });
}

// C-1: BlockButton은 이제 BlockedUsersProvider의 공유 상태로 초기값·낙관적 갱신을 한다 —
// 프로덕션(layout.tsx)과 같은 중첩 구조로 렌더해야 실제 동작을 검증할 수 있다.
function renderBlockButton(userId: number) {
  return render(
    <CommunitySessionProvider>
      <BlockedUsersProvider>
        <BlockButton userId={userId} />
      </BlockedUsersProvider>
    </CommunitySessionProvider>
  );
}

describe("사용자 차단 버튼", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  // FE-057: 되돌리기 어려운 작업인데 클릭 즉시 실행됐다.
  it("차단 버튼을 눌러도 확인 전에는 차단 요청을 보내지 않는다", async () => {
    const fetchSpy = vi.fn();
    global.fetch = mockFetch((url, init) => {
      fetchSpy(url, init);
      return { status: 204, body: null };
    });

    renderBlockButton(2);

    fireEvent.click(await screen.findByRole("button", { name: "차단" }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalledWith(
      expect.stringContaining("/block"),
      expect.anything()
    );
  });

  it("확인 다이얼로그에서 차단을 누르면 차단하고 해제 경로를 제공한다", async () => {
    global.fetch = mockFetch();

    renderBlockButton(2);

    fireEvent.click(await screen.findByRole("button", { name: "차단" }));
    // 다이얼로그 안의 확인 버튼
    const dialog = screen.getByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "차단" }));

    expect(await screen.findByText("차단했습니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "차단 해제" })).toBeInTheDocument();
  });

  it("차단 해제를 누르면 다시 차단할 수 있는 상태로 돌아온다", async () => {
    const calls: string[] = [];
    global.fetch = mockFetch((url, init) => {
      calls.push(`${init?.method ?? "GET"} ${url}`);
      return { status: 204, body: null };
    });

    renderBlockButton(2);

    fireEvent.click(await screen.findByRole("button", { name: "차단" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "차단" }));
    fireEvent.click(await screen.findByRole("button", { name: "차단 해제" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "차단" })).toBeInTheDocument();
    });
    expect(calls.some((call) => call.includes("/block"))).toBe(true);
  });

  // C-1: 예전에는 항상 useState(false)로 시작해 새로고침하면 이미 차단한 상대에게도
  // 다시 "차단" 버튼이 보였다.
  it("이미 차단한 사용자는 마운트 시점부터 차단 해제 상태로 보인다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/session")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
      }
      if (url.includes("/blocked-users")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([2]) });
      }
      return Promise.resolve({ ok: true, status: 204, json: () => Promise.resolve(null) });
    });

    renderBlockButton(2);

    expect(await screen.findByText("차단했습니다.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "차단" })).not.toBeInTheDocument();
  });

  it("본인에게는 차단 버튼을 보여주지 않는다", async () => {
    global.fetch = mockFetch();

    renderBlockButton(1);

    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "차단" })).not.toBeInTheDocument();
    });
  });
});
