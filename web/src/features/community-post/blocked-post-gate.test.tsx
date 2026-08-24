import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionContext, type SessionState } from "@/entities/user-session/session-context";
import { resetResourceCacheForTests } from "@/shared/hooks/use-resource";

import { BlockButton, BlockedPostGate } from "./blocked-post-gate";

const { getMyInteractions, blockUser } = vi.hoisted(() => ({
  getMyInteractions: vi.fn(),
  blockUser: vi.fn(),
}));

vi.mock("@/entities/community-post/interactions", () => ({
  getMyInteractions,
  blockUser,
}));

function interactions(blockedUserIds: number[]) {
  return { likedPostIds: [], bookmarkedPostIds: [], blockedUserIds };
}

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
    resetResourceCacheForTests();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("비로그인은 항상 본문을 그대로 본다", () => {
    render(
      <SessionContext.Provider value={ANONYMOUS}>
        <BlockedPostGate postId={1} ownerId={99}>
          <p>본문</p>
        </BlockedPostGate>
      </SessionContext.Provider>,
    );
    expect(screen.getByText("본문")).toBeInTheDocument();
    expect(getMyInteractions).not.toHaveBeenCalled();
  });

  it("차단 목록에 없는 작성자의 글은 그대로 보인다", async () => {
    getMyInteractions.mockResolvedValue(interactions([]));
    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockedPostGate postId={1} ownerId={99}>
          <p>본문</p>
        </BlockedPostGate>
      </SessionContext.Provider>,
    );
    await waitFor(() => expect(getMyInteractions).toHaveBeenCalledWith([1]));
    expect(screen.getByText("본문")).toBeInTheDocument();
  });

  it("차단한 작성자의 글은 가리고, 이번만 보기로 드러낼 수 있다", async () => {
    const user = userEvent.setup();
    getMyInteractions.mockResolvedValue(interactions([99]));
    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockedPostGate postId={1} ownerId={99}>
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
    getMyInteractions.mockRejectedValue(new Error("network"));
    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockedPostGate postId={1} ownerId={99}>
          <p>본문</p>
        </BlockedPostGate>
      </SessionContext.Provider>,
    );
    await waitFor(() => expect(getMyInteractions).toHaveBeenCalled());
    expect(screen.getByText("본문")).toBeInTheDocument();
  });

  // FE-DATA-01(docs/improvement.md): 같은 postId를 쓰는 두 소비자(게이트·버튼)가
  // 동일 리소스 키를 공유해 실제 네트워크 요청이 1회로 dedupe되는지 확인한다.
  it("같은 postId의 게이트와 버튼이 마운트돼도 getMyInteractions는 1회만 호출된다", async () => {
    getMyInteractions.mockResolvedValue(interactions([]));
    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockedPostGate postId={1} ownerId={99}>
          <p>본문</p>
        </BlockedPostGate>
        <BlockButton postId={1} ownerId={99} />
      </SessionContext.Provider>,
    );

    await screen.findByRole("button", { name: "이 사용자 차단" });
    expect(getMyInteractions).toHaveBeenCalledTimes(1);
  });
});

describe("차단·해제 버튼", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetResourceCacheForTests();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("본인 글에서는 렌더하지 않는다", () => {
    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockButton postId={1} ownerId={10} />
      </SessionContext.Provider>,
    );
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("남의 글에서 눌러 차단 상태를 토글한다", async () => {
    const user = userEvent.setup();
    getMyInteractions.mockResolvedValue(interactions([]));
    blockUser.mockResolvedValue(undefined);

    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockButton postId={1} ownerId={99} />
      </SessionContext.Provider>,
    );

    const button = await screen.findByRole("button", { name: "이 사용자 차단" });
    await user.click(button);

    await waitFor(() => expect(blockUser).toHaveBeenCalledWith(99, true));
    expect(await screen.findByRole("button", { name: "차단 해제" })).toBeInTheDocument();
  });

  it("차단 토글 성공 후 같은 postId의 게이트도 함께 갱신된다", async () => {
    const user = userEvent.setup();
    getMyInteractions.mockResolvedValueOnce(interactions([]));
    blockUser.mockResolvedValue(undefined);

    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockedPostGate postId={1} ownerId={99}>
          <p>본문</p>
        </BlockedPostGate>
        <BlockButton postId={1} ownerId={99} />
      </SessionContext.Provider>,
    );

    expect(await screen.findByText("본문")).toBeInTheDocument();

    getMyInteractions.mockResolvedValueOnce(interactions([99]));
    await user.click(screen.getByRole("button", { name: "이 사용자 차단" }));

    await waitFor(() => expect(getMyInteractions).toHaveBeenCalledTimes(2));
    expect(await screen.findByText("차단한 사용자의 글입니다")).toBeInTheDocument();
  });

  it("차단 요청이 실패하면 낙관적 상태를 되돌리고 오류를 보여준다", async () => {
    const user = userEvent.setup();
    getMyInteractions.mockResolvedValue(interactions([]));
    blockUser.mockRejectedValue(new Error("network"));

    render(
      <SessionContext.Provider value={LOGGED_IN}>
        <BlockButton postId={1} ownerId={99} />
      </SessionContext.Provider>,
    );

    const button = await screen.findByRole("button", { name: "이 사용자 차단" });
    await user.click(button);

    expect(await screen.findByText("차단 상태를 변경하지 못했습니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "이 사용자 차단" })).toBeInTheDocument();
  });
});
