import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PostForm } from "@/features/community/post-form";
import { CommunitySessionProvider } from "@/lib/community-session-provider";

const replace = vi.fn();
const push = vi.fn();
const refresh = vi.fn();
const revalidateCommunityPost = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push, refresh }),
  // KF-05: CommunitySessionProvider가 usePathname으로 세션 스코프를 판단한다 — 이 컴포넌트는
  // 항상 /community 하위에서만 쓰이므로 스코프 안 경로로 고정해 기존 동작을 유지한다.
  usePathname: () => "/community/write",
}));

vi.mock("@/lib/community-revalidate", () => ({
  revalidateCommunityPost: (...args: unknown[]) => revalidateCommunityPost(...args),
}));

function renderPostForm(props: Parameters<typeof PostForm>[0]) {
  return render(
    <CommunitySessionProvider>
      <PostForm {...props} />
    </CommunitySessionProvider>
  );
}

function mockFetch(handlers: {
  session?: unknown;
  onWrite?: (url: string, init?: RequestInit) => { status: number; body: unknown };
}) {
  return vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    if (url.includes("/session")) {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ activeProviders: [], ...(handlers.session as object) }),
      });
    }
    const { status, body } = handlers.onWrite?.(url, init) ?? { status: 200, body: {} };
    return Promise.resolve({
      ok: status >= 200 && status < 300,
      status,
      json: () => Promise.resolve(body),
    });
  });
}

describe("커뮤니티 게시글 작성·수정 폼", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    replace.mockClear();
    push.mockClear();
    refresh.mockClear();
    revalidateCommunityPost.mockClear();
    window.sessionStorage.clear();
  });

  it("로그인하지 않은 사용자에게는 로그인 안내를 보여주고 리다이렉트하지 않는다", async () => {
    global.fetch = mockFetch({
      session: { loggedIn: false, userId: null, nickname: null, activeProviders: ["google", "naver"] },
    });

    renderPostForm({ mode: "create" });

    expect(await screen.findByText("이 기능을 사용하려면 로그인이 필요합니다.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Google 로그인" })).toHaveAttribute(
      "href",
      "/oauth2/authorization/google"
    );
    expect(replace).not.toHaveBeenCalled();
  });

  // FE-005: 세션 조회 실패를 비로그인으로 축약하면 activeProviders가 비어 로그인 링크까지
  // 사라진다 — 사용자가 복구할 방법이 없어진다. 실패는 실패로 알리고 재시도를 제공한다.
  it("세션 조회에 실패하면 비로그인이 아니라 오류와 재시도를 보여준다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/session")) return Promise.reject(new Error("network down"));
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });

    renderPostForm({ mode: "create" });

    expect(await screen.findByText("로그인 상태를 확인하지 못했습니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
    // 비로그인 안내로 잘못 표시하지 않아야 한다.
    expect(screen.queryByText("이 기능을 사용하려면 로그인이 필요합니다.")).not.toBeInTheDocument();
  });

  it("세션 조회 실패 후 다시 시도하면 정상 상태로 복구된다", async () => {
    let shouldFail = true;
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/session")) {
        if (shouldFail) return Promise.reject(new Error("network down"));
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ loggedIn: false, userId: null, nickname: null, activeProviders: ["google"] }),
        });
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });

    renderPostForm({ mode: "create" });

    const retryButton = await screen.findByRole("button", { name: "다시 시도" });
    // 재시도 클릭이 곧바로 effect를 다시 돌리므로 성공 응답으로 먼저 바꿔 둔다.
    shouldFail = false;
    fireEvent.click(retryButton);

    expect(await screen.findByRole("link", { name: "Google 로그인" })).toBeInTheDocument();
  });

  it("로그인 링크를 클릭하면 복귀 경로를 저장한다", async () => {
    global.fetch = mockFetch({
      session: { loggedIn: false, userId: null, nickname: null, activeProviders: ["google"] },
    });

    renderPostForm({ mode: "create" });

    fireEvent.click(await screen.findByRole("link", { name: "Google 로그인" }));

    expect(window.sessionStorage.getItem("kraft-community-return-to")).toBe("/community/write");
  });

  it("게시글 수정 중 버전 충돌(409)이 나면 안내 메시지를 보여주고 입력값을 유지한다", async () => {
    global.fetch = mockFetch({
      session: { loggedIn: true, userId: 1, nickname: "글쓴이" },
      onWrite: () => ({
        status: 409,
        body: { code: "COMMUNITY_POST_VERSION_CONFLICT", message: "충돌" },
      }),
    });

    renderPostForm({
      mode: "edit",
      postId: 1,
      ownerId: 1,
      initialTitle: "원래 제목",
      initialContent: "원래 내용",
      initialVersion: 0,
    });

    const titleInput = await screen.findByLabelText("제목");
    fireEvent.change(titleInput, { target: { value: "수정된 제목" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText(/다른 곳에서 먼저 수정되었습니다/)).toBeInTheDocument();
    });
    expect(titleInput).toHaveValue("수정된 제목");
  });

  // FE-066: 충돌 안내가 "새로고침한 뒤 다시 작성해 주세요"였는데, 새로고침하면 작성 중이던
  // 내용이 사라진다. 입력값은 유지한 채 최신 version만 받아 이어서 저장할 수 있어야 한다.
  it("버전 충돌 후 최신 상태를 불러오면 입력값을 유지한 채 다시 저장할 수 있다", async () => {
    let conflicted = false;
    const putBodies: string[] = [];
    global.fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url.includes("/session")) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ loggedIn: true, userId: 1, nickname: "글쓴이", activeProviders: [] }),
        });
      }
      if (init?.method === "PUT") {
        putBodies.push(init.body as string);
        if (!conflicted) {
          conflicted = true;
          return Promise.resolve({
            ok: false,
            status: 409,
            json: () => Promise.resolve({ code: "COMMUNITY_POST_VERSION_CONFLICT", message: "충돌" }),
          });
        }
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ id: 1 }) });
      }
      // 충돌 후 최신 상태 재조회 — 다른 사람이 version 5로 올려 둔 상태.
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ id: 1, title: "남이 바꾼 제목", content: "남의 내용", version: 5 }),
      });
    });

    renderPostForm({
      mode: "edit",
      postId: 1,
      ownerId: 1,
      initialTitle: "원래 제목",
      initialContent: "원래 내용",
      initialVersion: 0,
    });

    const titleInput = await screen.findByLabelText("제목");
    fireEvent.change(titleInput, { target: { value: "내가 쓴 제목" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    fireEvent.click(await screen.findByRole("button", { name: "최신 상태 불러오기" }));

    // 상대가 무엇으로 바꿨는지 알려 주되, 내 입력은 그대로 남아 있어야 한다.
    expect(await screen.findByText(/남이 바꾼 제목/)).toBeInTheDocument();
    expect(titleInput).toHaveValue("내가 쓴 제목");
    expect(screen.queryByText(/다른 곳에서 먼저 수정되었습니다/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(putBodies).toHaveLength(2));
    // 두 번째 저장은 재조회한 최신 버전으로 나가야 한다 — 아니면 또 충돌한다.
    expect(JSON.parse(putBodies[0]).expectedVersion).toBe(0);
    expect(JSON.parse(putBodies[1]).expectedVersion).toBe(5);
    expect(JSON.parse(putBodies[1]).title).toBe("내가 쓴 제목");
  });

  it("본인이 아닌 게시글을 수정하려 하면 권한 안내와 돌아가기 링크를 보여준다", async () => {
    global.fetch = mockFetch({ session: { loggedIn: true, userId: 2, nickname: "다른사람" } });

    renderPostForm({
      mode: "edit",
      postId: 5,
      ownerId: 1,
      initialTitle: "원래 제목",
      initialContent: "원래 내용",
      initialVersion: 0,
    });

    expect(await screen.findByText("본인이 작성한 글만 수정할 수 있습니다.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "게시글로 돌아가기" })).toHaveAttribute(
      "href",
      "/community/posts/5"
    );
    expect(replace).not.toHaveBeenCalled();
  });

  it("게시글 작성에 성공하면 캐시를 무효화하고 새 글 상세 페이지로 이동한다", async () => {
    global.fetch = mockFetch({
      session: { loggedIn: true, userId: 1, nickname: "글쓴이" },
      onWrite: () => ({ status: 201, body: { id: 42 } }),
    });

    renderPostForm({ mode: "create" });

    fireEvent.change(await screen.findByLabelText("제목"), { target: { value: "제목" } });
    fireEvent.change(screen.getByLabelText("내용"), { target: { value: "내용" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/community/posts/42"));
    expect(revalidateCommunityPost).toHaveBeenCalledWith(42);
    expect(refresh).toHaveBeenCalled();
  });

  it("게시글 수정에 성공하면 캐시를 무효화하고 상세 페이지로 이동한다", async () => {
    global.fetch = mockFetch({
      session: { loggedIn: true, userId: 1, nickname: "글쓴이" },
      onWrite: () => ({ status: 200, body: { id: 7 } }),
    });

    renderPostForm({
      mode: "edit",
      postId: 7,
      ownerId: 1,
      initialTitle: "원래 제목",
      initialContent: "원래 내용",
      initialVersion: 0,
    });

    fireEvent.change(await screen.findByLabelText("제목"), { target: { value: "수정된 제목" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/community/posts/7"));
    expect(revalidateCommunityPost).toHaveBeenCalledWith(7);
    expect(refresh).toHaveBeenCalled();
  });

  it("버전 충돌이 아닌 저장 실패는 일반 오류 메시지를 보여준다", async () => {
    global.fetch = mockFetch({
      session: { loggedIn: true, userId: 1, nickname: "글쓴이" },
      onWrite: () => ({ status: 500, body: {} }),
    });

    renderPostForm({ mode: "create" });

    fireEvent.change(await screen.findByLabelText("제목"), { target: { value: "제목" } });
    fireEvent.change(screen.getByLabelText("내용"), { target: { value: "내용" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByText("저장에 실패했습니다. 잠시 후 다시 시도하세요.")).toBeInTheDocument();
    });
    expect(replace).not.toHaveBeenCalled();
  });

  it("저장 중에는 버튼이 비활성화되어 중복 제출을 막는다", async () => {
    let resolveWrite: (() => void) | undefined;
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/session")) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () =>
            Promise.resolve({ loggedIn: true, userId: 1, nickname: "글쓴이", activeProviders: [] }),
        });
      }
      return new Promise((resolve) => {
        resolveWrite = () =>
          resolve({ ok: true, status: 201, json: () => Promise.resolve({ id: 1 }) });
      });
    });

    renderPostForm({ mode: "create" });

    fireEvent.change(await screen.findByLabelText("제목"), { target: { value: "제목" } });
    fireEvent.change(screen.getByLabelText("내용"), { target: { value: "내용" } });

    const submitButton = screen.getByRole("button", { name: "저장" });
    fireEvent.click(submitButton);

    await waitFor(() => expect(screen.getByRole("button", { name: "저장 중…" })).toBeDisabled());

    resolveWrite?.();
    await waitFor(() => expect(push).toHaveBeenCalled());
  });
});
