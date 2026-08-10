import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionContext, type SessionState } from "@/entities/user-session/session-context";
import { ApiError } from "@/shared/api/error";

import { PostOwnerActions } from "./post-owner-actions";

const { deletePost, push, replace, refresh } = vi.hoisted(() => ({
  deletePost: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock("@/entities/community-post/interactions", () => ({ deletePost }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace, refresh, back: vi.fn() }),
}));

const OWNER: SessionState = {
  session: { loggedIn: true, userId: 10, nickname: "나", activeProviders: ["google"] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

const OTHER_USER: SessionState = {
  session: { loggedIn: true, userId: 99, nickname: "다른사람", activeProviders: ["google"] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

function renderActions(session: SessionState) {
  return render(
    <SessionContext.Provider value={session}>
      <PostOwnerActions postId={1} ownerId={10} version={3} />
    </SessionContext.Provider>,
  );
}

describe("게시글 소유자 액션", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("소유자가 아니면 렌더하지 않는다", () => {
    renderActions(OTHER_USER);
    expect(screen.queryByText("수정")).not.toBeInTheDocument();
    expect(screen.queryByText("삭제")).not.toBeInTheDocument();
  });

  it("소유자에게는 수정·삭제를 보여준다", () => {
    renderActions(OWNER);
    expect(screen.getByText("수정")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "삭제" })).toBeInTheDocument();
  });

  it("삭제를 확인하면 목록으로 이동하고 새로고침한다", async () => {
    const user = userEvent.setup();
    deletePost.mockResolvedValue(undefined);

    renderActions(OWNER);
    await user.click(screen.getByRole("button", { name: "삭제" }));

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "삭제" }));

    await waitFor(() => expect(deletePost).toHaveBeenCalledWith(1, 3));
    expect(replace).toHaveBeenCalledWith("/community");
    expect(refresh).toHaveBeenCalled();
  });

  it("409 충돌이면 새로고침 안내를 보여주고 이동하지 않는다", async () => {
    const user = userEvent.setup();
    deletePost.mockRejectedValue(new ApiError("client", "충돌", { status: 409 }));

    renderActions(OWNER);
    await user.click(screen.getByRole("button", { name: "삭제" }));

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "삭제" }));

    expect(
      await screen.findByText(
        "다른 곳에서 이 글이 수정됐습니다. 새로고침해 최신 내용을 확인한 뒤 다시 시도해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
