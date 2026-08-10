import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionContext, type SessionState } from "@/entities/user-session/session-context";

import { BlockButton, BlockedPostGate } from "./blocked-post-gate";

const { getBlockedUsers, blockUser } = vi.hoisted(() => ({
  getBlockedUsers: vi.fn(),
  blockUser: vi.fn(),
}));

vi.mock("@/entities/community-post/interactions", () => ({
  getBlockedUsers,
  blockUser,
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

describe("차단 게이팅", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("비로그인은 항상 본문을 그대로 본다", () => {
    render(
      <SessionContext.Provider value={ANONYMOUS}>
        <BlockedPostGate ownerId={99}>
          <p>본문</p>
        </BlockedPostGate>
      </SessionContext.Provider>,
    );
    expect(screen.getByText("본문")).toBeInTheDocument();
  });

  it("차단 목록에 없는 작성자의 글은 그대로 보인다", async () => {
    getBlockedUsers.mockResolvedValue([]);
    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockedPostGate ownerId={99}>
          <p>본문</p>
        </BlockedPostGate>
      </SessionContext.Provider>,
    );
    await waitFor(() => expect(getBlockedUsers).toHaveBeenCalled());
    expect(screen.getByText("본문")).toBeInTheDocument();
  });

  it("차단한 작성자의 글은 가리고, 이번만 보기로 드러낼 수 있다", async () => {
    const user = userEvent.setup();
    getBlockedUsers.mockResolvedValue([99]);
    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockedPostGate ownerId={99}>
          <p>본문</p>
        </BlockedPostGate>
      </SessionContext.Provider>,
    );

    expect(await screen.findByText("차단한 사용자의 글입니다")).toBeInTheDocument();
    expect(screen.queryByText("본문")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "이번만 보기" }));
    expect(screen.getByText("본문")).toBeInTheDocument();
  });

  it("차단 목록을 못 읽으면 가리지 않는다", async () => {
    getBlockedUsers.mockRejectedValue(new Error("network"));
    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockedPostGate ownerId={99}>
          <p>본문</p>
        </BlockedPostGate>
      </SessionContext.Provider>,
    );
    await waitFor(() => expect(getBlockedUsers).toHaveBeenCalled());
    expect(screen.getByText("본문")).toBeInTheDocument();
  });
});

describe("차단·해제 버튼", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("본인 글에서는 렌더하지 않는다", () => {
    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockButton ownerId={10} />
      </SessionContext.Provider>,
    );
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("남의 글에서 눌러 차단 상태를 토글한다", async () => {
    const user = userEvent.setup();
    getBlockedUsers.mockResolvedValue([]);
    blockUser.mockResolvedValue(undefined);

    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockButton ownerId={99} />
      </SessionContext.Provider>,
    );

    const button = await screen.findByRole("button", { name: "이 사용자 차단" });
    await user.click(button);

    await waitFor(() => expect(blockUser).toHaveBeenCalledWith(99, true));
    expect(await screen.findByRole("button", { name: "차단 해제" })).toBeInTheDocument();
  });
});
