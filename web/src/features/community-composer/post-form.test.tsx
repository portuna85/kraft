import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { CommunityPost } from "@/entities/community-post/schema";
import { SessionContext, type SessionState } from "@/entities/user-session/session-context";
import { ApiError } from "@/shared/api/error";
import { resetResourceCacheForTests } from "@/shared/hooks/use-resource";

import { PostForm } from "./post-form";

const {
  updatePost,
  createPost,
  fetchLatestPost,
  push,
  refresh,
  listAccountRecommendationSets,
  listDeviceRecommendationSets,
} = vi.hoisted(() => ({
  updatePost: vi.fn(),
  createPost: vi.fn(),
  fetchLatestPost: vi.fn(),
  push: vi.fn(),
  refresh: vi.fn(),
  listAccountRecommendationSets: vi.fn(),
  listDeviceRecommendationSets: vi.fn(),
}));

vi.mock("@/entities/community-post/composer", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/entities/community-post/composer")>()),
  updatePost,
  createPost,
  fetchLatestPost,
}));

vi.mock("@/entities/recommendation/api", () => ({
  listAccountRecommendationSets,
  listDeviceRecommendationSets,
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
      <a href="/community">커뮤니티로</a>
      <PostForm existing={existing} />
    </SessionContext.Provider>,
  );
}

describe("글 작성·수정 폼", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // KF-22(docs/improvement.md): useResource의 캐시가 모듈 스코프라 테스트
    // 간에 새지 않게 리셋한다.
    resetResourceCacheForTests();
    listAccountRecommendationSets.mockResolvedValue({
      items: [],
      page: 0,
      totalPages: 1,
      totalElements: 0,
    });
    listDeviceRecommendationSets.mockResolvedValue({
      items: [],
      page: 0,
      totalPages: 1,
      totalElements: 0,
    });
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

  it("본인 글이 아니면 폼 대신 권한 없음 안내를 본다 (§25.1)", () => {
    renderForm(post({ ownerId: 999 }));

    expect(screen.queryByLabelText("제목")).not.toBeInTheDocument();
    expect(screen.getByText("수정할 수 없습니다")).toBeInTheDocument();
  });

  // I-23: 분류가 발행 후 영구 고정이라 잘못 분류한 글을 고칠 방법이 없었다 —
  // 수정 화면에도 분류 선택을 둔다.
  it("수정 화면에도 분류를 둔다", () => {
    renderForm(post());
    expect(screen.getByLabelText("분류")).toBeInTheDocument();
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

  it("401(로그인 만료)이면 쓴 내용을 지우지 않고 로그인 안내를 보여준다 (§25.1)", async () => {
    const user = userEvent.setup();
    updatePost.mockRejectedValue(new ApiError("client", "미인증", { status: 401 }));

    renderForm(post());
    const title = screen.getByLabelText("제목");
    await user.clear(title);
    await user.type(title, "내가 쓴 제목");
    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText(/로그인이 만료됐습니다/)).toBeInTheDocument();
    expect(screen.getByLabelText("제목")).toHaveValue("내가 쓴 제목");
    expect(push).not.toHaveBeenCalled();
  });

  it("403(CSRF 만료)이면 새로고침 안내를 보여준다 (§25.1)", async () => {
    const user = userEvent.setup();
    updatePost.mockRejectedValue(new ApiError("client", "CSRF", { status: 403 }));

    renderForm(post());
    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText(/페이지를 새로 고친 뒤/)).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });

  it("작성(신규) 중 401이 나도 로그인 안내로 분기한다", async () => {
    const user = userEvent.setup();
    createPost.mockRejectedValue(new ApiError("client", "미인증", { status: 401 }));

    renderForm();
    await user.type(screen.getByLabelText("제목"), "새 글 제목");
    await user.type(screen.getByLabelText("내용"), "새 글 내용");
    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText(/로그인이 만료됐습니다/)).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });

  it("저장에 성공하면 상세로 이동한다", async () => {
    const user = userEvent.setup();
    updatePost.mockResolvedValue(post());

    renderForm(post());
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/community/posts/1"));
  });

  it("H-03: 첨부를 선택하지 않으면 createPost를 recommendationSetId=null로 호출한다", async () => {
    const user = userEvent.setup();
    createPost.mockResolvedValue(post());

    renderForm();
    await user.type(screen.getByLabelText("제목"), "새 글 제목");
    await user.type(screen.getByLabelText("내용"), "새 글 내용");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(createPost).toHaveBeenCalledWith(expect.anything(), null));
  });

  it("H-03: 추천 세트를 선택하면 그 id로 createPost를 호출한다", async () => {
    listAccountRecommendationSets.mockResolvedValue({
      items: [
        {
          id: 42,
          strategy: "random",
          createdAt: "2026-08-01T12:00:00Z",
          historyThroughRound: 1149,
          items: [{ position: 0, numbers: [1, 2, 3, 4, 5, 6], score: null, explanationCodes: [] }],
        },
      ],
      page: 0,
      totalPages: 1,
      totalElements: 1,
    });
    createPost.mockResolvedValue(post());
    const user = userEvent.setup();

    renderForm();
    await user.type(screen.getByLabelText("제목"), "새 글 제목");
    await user.type(screen.getByLabelText("내용"), "새 글 내용");

    const radios = await screen.findAllByRole("radio");
    // 첫 옵션은 "첨부 안 함" — 두 번째가 유일한 세트 옵션이다(fixture가 보장).
    await user.click(radios[1] as HTMLElement);

    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(createPost).toHaveBeenCalledWith(expect.anything(), 42));
  });

  // KF-11(docs/improvement.md): beforeunload는 하드 새로고침·탭 닫기만 막았다 —
  // 취소 버튼과 인앱 링크(브레드크럼·탭바 등)는 확인 없이 바로 나가졌다.
  describe("KF-11: 더티 폼 이탈 가드", () => {
    it("변경 없는 폼은 취소를 누르면 확인 없이 바로 뒤로 간다", async () => {
      const user = userEvent.setup();
      renderForm();

      await user.click(screen.getByRole("button", { name: "취소" }));

      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("더티 폼에서 취소를 누르면 확인창이 뜨고, 확인해야만 뒤로 간다", async () => {
      const user = userEvent.setup();
      renderForm();

      await user.type(screen.getByLabelText("제목"), "쓰다 만 제목");
      await user.click(screen.getByRole("button", { name: "취소" }));

      const dialog = await screen.findByRole("dialog");
      expect(dialog).toHaveTextContent("작성 중인 내용을 두고 나갈까요?");

      await user.click(screen.getByRole("button", { name: "나가기" }));
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("더티 폼에서 취소 확인창을 취소하면 폼 내용이 그대로 남는다", async () => {
      const user = userEvent.setup();
      renderForm();

      await user.type(screen.getByLabelText("제목"), "쓰다 만 제목");
      await user.click(screen.getByRole("button", { name: "취소" }));
      await screen.findByRole("dialog");

      await user.click(screen.getByRole("button", { name: "계속 작성" }));

      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      expect(screen.getByLabelText("제목")).toHaveValue("쓰다 만 제목");
    });

    it("더티 폼에서 인앱 링크를 클릭하면 가로채 확인창을 띄우고, 확인하면 그 경로로 이동한다", async () => {
      const user = userEvent.setup();
      renderForm();

      await user.type(screen.getByLabelText("제목"), "쓰다 만 제목");
      await user.click(screen.getByRole("link", { name: "커뮤니티로" }));

      const dialog = await screen.findByRole("dialog");
      expect(dialog).toBeInTheDocument();
      expect(push).not.toHaveBeenCalled();

      await user.click(screen.getByRole("button", { name: "나가기" }));
      expect(push).toHaveBeenCalledWith("/community");
    });

    it("제출 성공은 자체 이동이라 이탈 확인창을 띄우지 않는다", async () => {
      const user = userEvent.setup();
      updatePost.mockResolvedValue(post());

      renderForm(post());
      await user.clear(screen.getByLabelText("제목"));
      await user.type(screen.getByLabelText("제목"), "고친 제목");
      await user.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => expect(push).toHaveBeenCalledWith("/community/posts/1"));
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });
});
