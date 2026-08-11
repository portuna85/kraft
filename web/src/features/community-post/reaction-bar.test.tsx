import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionContext, type SessionState } from "@/entities/user-session/session-context";

import { ReactionBar } from "./reaction-bar";

const { likePost, bookmarkPost, getMyInteractions } = vi.hoisted(() => ({
  likePost: vi.fn(),
  bookmarkPost: vi.fn(),
  getMyInteractions: vi.fn(),
}));

vi.mock("@/entities/community-post/interactions", () => ({
  likePost,
  bookmarkPost,
  getMyInteractions,
}));

const LOGGED_IN: SessionState = {
  session: { loggedIn: true, userId: 10, nickname: "나", activeProviders: ["google"] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

const ANONYMOUS: SessionState = {
  session: { loggedIn: false, userId: null, nickname: null, activeProviders: [] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

function render_(session: SessionState) {
  return render(
    <SessionContext.Provider value={session}>
      <ReactionBar postId={1} initialLikeCount={3} />
    </SessionContext.Provider>,
  );
}

describe("좋아요·북마크", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getMyInteractions.mockResolvedValue({ likedPostIds: [], bookmarkedPostIds: [] });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("비로그인은 버튼 대신 안내 문구만 본다", () => {
    render_(ANONYMOUS);
    expect(
      screen.getByText("좋아요 3개 · 로그인하면 좋아요와 북마크를 남길 수 있습니다."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("좋아요를 누르면 낙관적으로 카운트를 올리고 반영한다", async () => {
    const user = userEvent.setup();
    likePost.mockResolvedValue(undefined);
    render_(LOGGED_IN);

    const button = screen.getByRole("button", { name: "좋아요 3" });
    await user.click(button);

    expect(screen.getByRole("button", { name: "좋아요 4" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await waitFor(() => expect(likePost).toHaveBeenCalledWith(1, true));
  });

  it("좋아요 반영 실패 시 낙관적 변경을 되돌린다", async () => {
    const user = userEvent.setup();
    likePost.mockRejectedValue(new Error("network"));
    render_(LOGGED_IN);

    await user.click(screen.getByRole("button", { name: "좋아요 3" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "좋아요 3" })).toHaveAttribute(
        "aria-pressed",
        "false",
      );
    });
    expect(screen.getByRole("alert")).toHaveTextContent("좋아요를 반영하지 못했습니다");
  });

  it("서버가 이미 좋아요한 상태를 알려주면 눌린 채로 시작한다", async () => {
    getMyInteractions.mockResolvedValue({ likedPostIds: [1], bookmarkedPostIds: [] });
    render_(LOGGED_IN);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "좋아요 3" })).toHaveAttribute(
        "aria-pressed",
        "true",
      );
    });
  });

  it("북마크를 누르면 낙관적으로 반영한다 (§25.6)", async () => {
    const user = userEvent.setup();
    bookmarkPost.mockResolvedValue(undefined);
    render_(LOGGED_IN);

    const button = await screen.findByRole("button", { name: "북마크" });
    await user.click(button);

    expect(screen.getByRole("button", { name: "북마크됨" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await waitFor(() => expect(bookmarkPost).toHaveBeenCalledWith(1, true));
  });

  it("북마크 반영 실패 시 낙관적 변경을 되돌린다", async () => {
    const user = userEvent.setup();
    bookmarkPost.mockRejectedValue(new Error("network"));
    render_(LOGGED_IN);

    await user.click(await screen.findByRole("button", { name: "북마크" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "북마크" })).toHaveAttribute(
        "aria-pressed",
        "false",
      );
    });
    expect(screen.getByRole("alert")).toHaveTextContent("북마크를 반영하지 못했습니다");
  });

  it("서버가 이미 북마크한 상태를 알려주면 눌린 채로 시작한다", async () => {
    getMyInteractions.mockResolvedValue({ likedPostIds: [], bookmarkedPostIds: [1] });
    render_(LOGGED_IN);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "북마크됨" })).toHaveAttribute(
        "aria-pressed",
        "true",
      );
    });
  });
});
