import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ReactionBar } from "@/features/community/reaction-bar";
import { CommunitySessionProvider } from "@/lib/community-session-provider";

vi.mock("next/navigation", () => ({
  usePathname: () => "/community/posts/1",
}));

function mockFetch(session: unknown) {
  return vi.fn().mockImplementation((url: string) => {
    if (url.includes("/session")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(session) });
    }
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ likedPostIds: [], bookmarkedPostIds: [] }) });
  });
}

function renderBar(session: unknown) {
  global.fetch = mockFetch(session);
  return render(
    <CommunitySessionProvider>
      <ReactionBar postId={1} initialLikeCount={3} />
    </CommunitySessionProvider>
  );
}

describe("게시글 반응 바", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    window.sessionStorage.clear();
  });

  // FE-060: 예전에는 안내 문구만 있고 로그인 경로가 없어 흐름이 끊겼다.
  it("비로그인 상태로 좋아요를 누르면 로그인 링크를 제공한다", async () => {
    renderBar({ loggedIn: false, userId: null, nickname: null, activeProviders: ["google", "naver"] });

    fireEvent.click(await screen.findByRole("button", { name: /좋아요/ }));

    expect(screen.getByText(/로그인 후 이용할 수 있습니다/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Google 로그인" })).toHaveAttribute(
      "href",
      "/oauth2/authorization/google"
    );
  });

  it("로그인 링크를 누르면 현재 경로를 복귀 경로로 저장한다", async () => {
    renderBar({ loggedIn: false, userId: null, nickname: null, activeProviders: ["google"] });

    fireEvent.click(await screen.findByRole("button", { name: /북마크/ }));
    fireEvent.click(screen.getByRole("link", { name: "Google 로그인" }));

    expect(window.sessionStorage.getItem("kraft-community-return-to")).toBe("/community/posts/1");
  });

  it("provider가 없으면 막다른 화면 대신 커뮤니티 진입 링크를 준다", async () => {
    renderBar({ loggedIn: false, userId: null, nickname: null, activeProviders: [] });

    fireEvent.click(await screen.findByRole("button", { name: /좋아요/ }));

    expect(screen.getByRole("link", { name: "로그인하러 가기" })).toHaveAttribute("href", "/community");
  });

  it("로그인 상태에서는 로그인 안내를 보여주지 않는다", async () => {
    renderBar({ loggedIn: true, userId: 1, nickname: "나", activeProviders: ["google"] });

    fireEvent.click(await screen.findByRole("button", { name: /좋아요/ }));

    expect(screen.queryByText(/로그인 후 이용할 수 있습니다/)).not.toBeInTheDocument();
  });
});
