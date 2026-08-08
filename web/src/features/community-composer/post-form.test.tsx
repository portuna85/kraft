import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { CommunityPost } from "@/entities/community-post/schema";
import { SessionContext, type SessionState } from "@/entities/user-session/session-context";
import { ApiError } from "@/shared/api/error";

import { PostForm } from "./post-form";

const { updatePost, createPost, fetchLatestPost, push, refresh } = vi.hoisted(() => ({
  updatePost: vi.fn(),
  createPost: vi.fn(),
  fetchLatestPost: vi.fn(),
  push: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock("@/entities/community-post/composer", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/entities/community-post/composer")>()),
  updatePost,
  createPost,
  fetchLatestPost,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, refresh, back: vi.fn() }),
}));

const LOGGED_IN: SessionState = {
  session: { loggedIn: true, userId: 10, nickname: "나", activeProviders: ["google"] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

function post(overrides: Partial<CommunityPost> = {}): CommunityPost {
  return {
    id: 1,
    ownerId: 10,
    authorNickname: "나",
    title: "원래 제목",
    content: "원래 내용",
    category: "GENERAL",
    status: "PUBLISHED",
    version: 3,
    createdAt: "2026-08-01T12:00:00Z",
    updatedAt: "2026-08-01T12:00:00Z",
    likeCount: 0,
    commentCount: 0,
    viewCount: 0,
    recommendationAttachment: null,
    ...overrides,
  };
}

function renderForm(existing?: CommunityPost, session: SessionState = LOGGED_IN) {
  return render(
    <SessionContext.Provider value={session}>
      <PostForm existing={existing} />
    </SessionContext.Provider>,
  );
}

describe("글 작성·수정 폼", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("비로그인은 폼 대신 로그인 안내를 본다", () => {
    renderForm(undefined, {
      ...LOGGED_IN,
      session: { loggedIn: false, userId: null, nickname: null, activeProviders: ["google"] },
    });

    expect(screen.queryByLabelText("제목")).not.toBeInTheDocument();
    expect(screen.getByText("로그인이 필요합니다")).toBeInTheDocument();
  });

  it("수정 화면에는 분류를 두지 않는다", () => {
    renderForm(post());
    expect(screen.queryByLabelText("분류")).not.toBeInTheDocument();
  });

  it("작성 화면에는 분류를 둔다", () => {
    renderForm();
    expect(screen.getByLabelText("분류")).toBeInTheDocument();
  });

  it("빈 제목으로는 저장을 보내지 않는다", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText("내용"), "내용만 있음");
    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(createPost).not.toHaveBeenCalled();
    expect(await screen.findByText("제목을 입력해 주세요.")).toBeInTheDocument();
  });

  it("409 충돌에도 쓴 내용을 지우지 않고 최신을 함께 보여준다", async () => {
    // 여기서 폼을 최신 내용으로 덮으면 사용자가 방금 쓴 글이 사라진다(레거시 FE-066).
    const user = userEvent.setup();
    updatePost.mockRejectedValue(new ApiError("client", "충돌", { status: 409 }));
    fetchLatestPost.mockResolvedValue(
      post({ title: "남이 고친 제목", content: "남이 고친 내용", version: 9 }),
    );

    renderForm(post());

    const title = screen.getByLabelText("제목");
    await user.clear(title);
    await user.type(title, "내가 쓴 제목");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText("남이 고친 제목")).toBeInTheDocument();
    });

    // 내가 쓴 내용은 그대로 남아 있어야 한다.
    expect(screen.getByLabelText("제목")).toHaveValue("내가 쓴 제목");
    expect(push).not.toHaveBeenCalled();
  });

  it("409 이후 재저장은 최신 버전으로 보낸다", async () => {
    const user = userEvent.setup();
    updatePost.mockRejectedValueOnce(new ApiError("client", "충돌", { status: 409 }));
    fetchLatestPost.mockResolvedValue(post({ version: 9 }));

    renderForm(post({ version: 3 }));
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(fetchLatestPost).toHaveBeenCalled());

    updatePost.mockResolvedValueOnce(post({ version: 10 }));
    await user.click(screen.getByRole("button", { name: "저장" }));

    // 옛 버전(3)으로 다시 보내면 영원히 409다.
    await waitFor(() => {
      expect(updatePost).toHaveBeenLastCalledWith(1, expect.anything(), 9);
    });
  });

  it("저장에 성공하면 상세로 이동한다", async () => {
    const user = userEvent.setup();
    updatePost.mockResolvedValue(post());

    renderForm(post());
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/community/posts/1"));
  });
});
