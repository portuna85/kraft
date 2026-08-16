import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { CommentPage, CommunityComment } from "@/entities/community-comment/schema";
import { SessionContext, type SessionState } from "@/entities/user-session/session-context";

import { CommentSection } from "./comment-section";

const { createComment, deleteComment, fetchCommentPage } = vi.hoisted(() => ({
  createComment: vi.fn(),
  deleteComment: vi.fn(),
  fetchCommentPage: vi.fn(),
}));

vi.mock("@/entities/community-comment/api", () => ({
  createComment,
  deleteComment,
  fetchCommentPage,
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

function comment(overrides: Partial<CommunityComment> = {}): CommunityComment {
  return {
    id: 1,
    postId: 100,
    parentId: null,
    ownerId: 10,
    authorNickname: "나",
    content: "댓글 내용",
    deleted: false,
    createdAt: "2026-08-01T12:00:00Z",
    targetPage: null,
    replies: [],
    ...overrides,
  };
}

function initialPage(overrides: Partial<CommentPage> = {}): CommentPage {
  return {
    topLevel: [comment()],
    totalTopLevelComments: 1,
    page: 0,
    size: 20,
    totalPages: 1,
    ...overrides,
  };
}

function renderSection(session: SessionState, page = initialPage()) {
  return render(
    <SessionContext.Provider value={session}>
      <CommentSection postId={100} initialPage={page} />
    </SessionContext.Provider>,
  );
}

describe("댓글 섹션", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("초기 페이지를 그대로 보여준다 — 마운트 즉시 재조회하지 않는다", () => {
    renderSection(LOGGED_IN);
    expect(screen.getByText("원댓글 1개")).toBeInTheDocument();
    expect(fetchCommentPage).not.toHaveBeenCalled();
  });

  it("댓글이 없으면 빈 안내를 보여준다", () => {
    renderSection(LOGGED_IN, initialPage({ topLevel: [], totalTopLevelComments: 0 }));
    expect(screen.getByText("아직 댓글이 없습니다")).toBeInTheDocument();
  });

  it("비로그인은 작성 폼 대신 로그인 안내를 본다", () => {
    renderSection(ANONYMOUS);
    expect(screen.queryByLabelText("댓글 작성")).not.toBeInTheDocument();
    expect(screen.getByText("로그인하면 댓글을 남길 수 있습니다.")).toBeInTheDocument();
  });

  it("댓글을 작성하면 목록을 새로고침한다", async () => {
    const user = userEvent.setup();
    createComment.mockResolvedValue(comment());
    fetchCommentPage.mockResolvedValue(initialPage({ totalTopLevelComments: 2 }));

    renderSection(LOGGED_IN);
    await user.type(screen.getByLabelText("댓글 작성"), "새 댓글");
    await user.click(screen.getByRole("button", { name: "등록" }));

    await waitFor(() => expect(createComment).toHaveBeenCalledWith(100, "새 댓글", null));
    await waitFor(() => expect(screen.getByText("원댓글 2개")).toBeInTheDocument());
  });

  // UX-03: 작성 후 목록만 새로고침되고 새 댓글로는 스크롤·강조가 없어 사용자가
  // 직접 찾아 내려가야 했다.
  it("작성한 댓글로 스크롤하고 강조 표시한다", async () => {
    const user = userEvent.setup();
    createComment.mockResolvedValue(comment({ id: 42 }));
    fetchCommentPage.mockResolvedValue(
      initialPage({ topLevel: [comment({ id: 42 })], totalTopLevelComments: 1 }),
    );

    renderSection(LOGGED_IN);
    await user.type(screen.getByLabelText("댓글 작성"), "새 댓글");
    await user.click(screen.getByRole("button", { name: "등록" }));

    await waitFor(() => expect(document.getElementById("comment-42")).toBeInTheDocument());
  });

  it("빈 내용으로는 등록 버튼이 비활성화된다", () => {
    renderSection(LOGGED_IN);
    expect(screen.getByRole("button", { name: "등록" })).toBeDisabled();
  });

  it("삭제를 확인하면 deleteComment를 부르고 목록을 새로고침한다 (§25.6)", async () => {
    const user = userEvent.setup();
    deleteComment.mockResolvedValue(null);
    fetchCommentPage.mockResolvedValue(initialPage({ topLevel: [], totalTopLevelComments: 0 }));

    renderSection(LOGGED_IN, initialPage({ topLevel: [comment({ id: 7, ownerId: 10 })] }));
    await user.click(screen.getByText("삭제"));

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "삭제" }));

    await waitFor(() => expect(deleteComment).toHaveBeenCalledWith(7));
    await waitFor(() => expect(fetchCommentPage).toHaveBeenCalledWith(100, 0));
    expect(await screen.findByText("원댓글 0개")).toBeInTheDocument();
  });

  it("삭제 실패 시 오류 문구를 보여주고 댓글을 유지한다", async () => {
    const user = userEvent.setup();
    deleteComment.mockRejectedValue(new Error("network"));

    renderSection(LOGGED_IN, initialPage({ topLevel: [comment({ id: 7, ownerId: 10 })] }));
    await user.click(screen.getByText("삭제"));

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "삭제" }));

    expect(
      await screen.findByText("댓글을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByText("원댓글 1개")).toBeInTheDocument();
  });

  it("본인 댓글에는 삭제 버튼을, 남의 댓글에는 신고 버튼을 보여준다", () => {
    renderSection(
      LOGGED_IN,
      initialPage({ topLevel: [comment({ ownerId: 10 }), comment({ id: 2, ownerId: 99 })] }),
    );
    expect(screen.getAllByText("삭제")).toHaveLength(1);
    expect(screen.getAllByText("신고")).toHaveLength(1);
  });
});
