import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { AccountMenu } from "@/components/community/account-menu";
import { CommunitySessionProvider } from "@/lib/community-session-provider";

// KF-05: 이 파일의 기존 케이스는 전부 세션이 실제로 확인되는 상황(/community)을
// 전제로 한다 — 스코프 밖(공개 페이지) 렌더링은 별도 테스트에서 pathname을 바꿔 검증한다.
const mockUsePathname = vi.fn(() => "/community");
vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
}));

function mockFetch(body: unknown) {
  return vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: () => Promise.resolve(body),
  });
}

function renderAccountMenu() {
  return render(
    <CommunitySessionProvider>
      <AccountMenu />
    </CommunitySessionProvider>
  );
}

describe("커뮤니티 계정 메뉴", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockUsePathname.mockReset().mockReturnValue("/community");
  });

  it("KF-05: 공개 페이지(스코프 밖)에서는 세션을 조회하지 않고 일반 로그인 링크만 보여준다", async () => {
    mockUsePathname.mockReturnValue("/stats");
    global.fetch = mockFetch({ loggedIn: true, userId: 1, nickname: "글쓴이", activeProviders: ["google"] });

    renderAccountMenu();

    const link = await screen.findByText("로그인");
    expect(link).toHaveAttribute("href", "/community");
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it("로그인하지 않은 경우 활성화된 Google·Naver 로그인 링크를 모두 보여준다", async () => {
    global.fetch = mockFetch({
      loggedIn: false,
      userId: null,
      nickname: null,
      activeProviders: ["google", "naver"],
    });

    renderAccountMenu();

    expect(await screen.findByText("Google 로그인")).toBeInTheDocument();
    expect(screen.getByText("Naver 로그인")).toBeInTheDocument();
  });

  it("Google만 활성화된 경우 Google 로그인 링크만 보여준다", async () => {
    global.fetch = mockFetch({
      loggedIn: false,
      userId: null,
      nickname: null,
      activeProviders: ["google"],
    });

    renderAccountMenu();

    expect(await screen.findByText("Google 로그인")).toBeInTheDocument();
    expect(screen.queryByText("Naver 로그인")).not.toBeInTheDocument();
  });

  it("Naver만 활성화된 경우 Naver 로그인 링크만 보여주고 축약 링크도 Naver를 가리킨다", async () => {
    global.fetch = mockFetch({
      loggedIn: false,
      userId: null,
      nickname: null,
      activeProviders: ["naver"],
    });

    renderAccountMenu();

    expect(await screen.findByText("Naver 로그인")).toBeInTheDocument();
    expect(screen.queryByText("Google 로그인")).not.toBeInTheDocument();
    const compactLink = screen.getByText("로그인");
    expect(compactLink).toHaveAttribute("href", "/oauth2/authorization/naver");
  });

  it("활성화된 provider가 없으면 아무 것도 렌더링하지 않는다", async () => {
    global.fetch = mockFetch({
      loggedIn: false,
      userId: null,
      nickname: null,
      activeProviders: [],
    });

    const { container } = renderAccountMenu();

    await waitFor(() => {
      expect(container).toBeEmptyDOMElement();
    });
  });

  it("로그인한 경우 닉네임과 로그아웃 버튼을 보여준다", async () => {
    global.fetch = mockFetch({
      loggedIn: true,
      userId: 1,
      nickname: "글쓴이",
      activeProviders: ["google", "naver"],
    });

    renderAccountMenu();

    await waitFor(() => {
      expect(screen.getByText("글쓴이님")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "로그아웃" })).toBeInTheDocument();
  });

  it("세션 조회가 실패하면 아무 것도 렌더링하지 않는다", async () => {
    global.fetch = vi
      .fn()
      .mockRejectedValueOnce(new Error("network error"))
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ loggedIn: false, userId: null, nickname: null, activeProviders: ["google"] }),
      });

    renderAccountMenu();

    expect(await screen.findByRole("alert")).toHaveTextContent("세션을 확인하지 못했습니다.");
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(await screen.findByText("Google 로그인")).toBeInTheDocument();
  });

  // FE-003: window.confirm 대신 공통 확인 다이얼로그를 쓴다. 트리거와 확인 버튼이
  // 같은 이름("탈퇴")이라 다이얼로그 안에서 찾아야 한다.
  it("KB-04: 탈퇴 확인 대화상자에서 취소하면 요청이 전송되지 않는다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/withdrawal")) {
        throw new Error("취소했는데 호출되면 안 된다");
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            loggedIn: true,
            userId: 1,
            nickname: "글쓴이",
            activeProviders: ["google", "naver"],
          }),
      });
    });

    renderAccountMenu();

    fireEvent.click(await screen.findByRole("button", { name: "탈퇴" }));

    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("정말 탈퇴할까요?");
    fireEvent.click(within(dialog).getByRole("button", { name: "취소" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("KB-04: 탈퇴를 확인하면 성공 시 새로고침한다", async () => {
    const reload = vi.fn();
    Object.defineProperty(window, "location", {
      value: { ...window.location, reload },
      writable: true,
    });

    global.fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (init?.method === "POST" && url.includes("/withdrawal")) {
        return Promise.resolve({ ok: true, status: 204, json: () => Promise.resolve({}) });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            loggedIn: true,
            userId: 1,
            nickname: "글쓴이",
            activeProviders: ["google", "naver"],
          }),
      });
    });

    renderAccountMenu();

    fireEvent.click(await screen.findByRole("button", { name: "탈퇴" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "탈퇴" }));

    await waitFor(() => {
      expect(reload).toHaveBeenCalled();
    });
  });

  it("KB-04: 탈퇴 요청이 실패하면 새로고침 없이 오류 문구를 보여준다", async () => {
    const reload = vi.fn();
    Object.defineProperty(window, "location", {
      value: { ...window.location, reload },
      writable: true,
    });

    global.fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (init?.method === "POST" && url.includes("/withdrawal")) {
        return Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            loggedIn: true,
            userId: 1,
            nickname: "글쓴이",
            activeProviders: ["google", "naver"],
          }),
      });
    });

    renderAccountMenu();

    fireEvent.click(await screen.findByRole("button", { name: "탈퇴" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "탈퇴" }));

    // 실패는 다이얼로그 안에서 알린다 — 닫아 버리면 사용자가 결과를 못 본다.
    expect(await screen.findByRole("alert")).toHaveTextContent("탈퇴에 실패했습니다");
    expect(reload).not.toHaveBeenCalled();
  });

  it("로그아웃 요청이 실패하면 새로고침 없이 오류 문구를 보여준다", async () => {
    const reload = vi.fn();
    Object.defineProperty(window, "location", {
      value: { ...window.location, reload },
      writable: true,
    });

    global.fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (init?.method === "POST" && url.includes("/logout")) {
        return Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            loggedIn: true,
            userId: 1,
            nickname: "글쓴이",
            activeProviders: ["google", "naver"],
          }),
      });
    });

    renderAccountMenu();

    const logoutButton = await screen.findByRole("button", { name: "로그아웃" });
    fireEvent.click(logoutButton);

    expect(await screen.findByRole("alert")).toHaveTextContent("로그아웃에 실패했습니다");
    expect(reload).not.toHaveBeenCalled();
  });
});
