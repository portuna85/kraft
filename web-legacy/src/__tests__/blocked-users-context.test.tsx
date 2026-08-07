import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { CommunitySessionProvider } from "@/lib/community-session-provider";
import { BlockedUsersProvider, useBlockedUserIds } from "@/features/community/blocked-users-context";

vi.mock("next/navigation", () => ({
  usePathname: () => "/community",
}));

const SESSION = { loggedIn: true, userId: 1, nickname: "나", activeProviders: ["google"] };
const ANON_SESSION = { loggedIn: false, userId: null, nickname: null, activeProviders: ["google"] };

function mockFetch(session: unknown, blockedUserIds: number[]) {
  return vi.fn().mockImplementation((url: string) => {
    if (url.includes("/session")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(session) });
    }
    if (url.includes("/blocked-users")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(blockedUserIds) });
    }
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(null) });
  });
}

function Probe() {
  const { isBlocked } = useBlockedUserIds();
  return (
    <ul>
      {[2, 3, 4].map((id) => (
        <li key={id}>{`${id}:${isBlocked(id)}`}</li>
      ))}
    </ul>
  );
}

function renderProbe() {
  return render(
    <CommunitySessionProvider>
      <BlockedUsersProvider>
        <Probe />
      </BlockedUsersProvider>
    </CommunitySessionProvider>
  );
}

describe("C-1: 커뮤니티 차단 목록 공유 컨텍스트", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("로그인 상태면 페이지 로드 시 차단 목록을 한 번 조회해 공유한다", async () => {
    global.fetch = mockFetch(SESSION, [2, 4]);

    renderProbe();

    await waitFor(() => expect(screen.getByText("2:true")).toBeInTheDocument());
    expect(screen.getByText("3:false")).toBeInTheDocument();
    expect(screen.getByText("4:true")).toBeInTheDocument();
  });

  it("비로그인 상태면 조회 자체를 하지 않고 전부 미차단으로 본다", async () => {
    const fetchMock = mockFetch(ANON_SESSION, [2]);
    global.fetch = fetchMock;

    renderProbe();

    await waitFor(() => expect(screen.getByText("2:false")).toBeInTheDocument());
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining("/blocked-users"), expect.anything());
  });

  it("조회에 실패해도 화면을 막지 않고 미차단으로 폴백한다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/session")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
      }
      return Promise.reject(new Error("network down"));
    });

    renderProbe();

    await waitFor(() => expect(screen.getByText("2:false")).toBeInTheDocument());
  });
});
