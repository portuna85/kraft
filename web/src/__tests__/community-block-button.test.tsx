import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { BlockButton } from "@/features/community/block-button";
import { CommunitySessionProvider } from "@/lib/community-session-provider";

vi.mock("next/navigation", () => ({
  usePathname: () => "/community/posts/1",
}));

const SESSION = { loggedIn: true, userId: 1, nickname: "나", activeProviders: ["google"] };

function mockFetch(onWrite?: (url: string, init?: RequestInit) => { status: number; body: unknown }) {
  return vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    if (url.includes("/session")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
    }
    const { status, body } = onWrite?.(url, init) ?? { status: 204, body: null };
    return Promise.resolve({
      ok: status >= 200 && status < 300,
      status,
      json: () => Promise.resolve(body),
    });
  });
}

function renderBlockButton(userId: number) {
  return render(
    <CommunitySessionProvider>
      <BlockButton userId={userId} />
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

  it("본인에게는 차단 버튼을 보여주지 않는다", async () => {
    global.fetch = mockFetch();

    renderBlockButton(1);

    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "차단" })).not.toBeInTheDocument();
    });
  });
});
