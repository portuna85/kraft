import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { CommentSection } from "@/features/community/comment-section";
import { CommunitySessionProvider } from "@/lib/community-session-provider";
import { BlockedUsersProvider } from "@/features/community/blocked-users-context";

// KF-05: CommunitySessionProvider가 usePathname으로 세션 스코프를 판단한다 — 이 컴포넌트는
// 항상 게시글 상세(/community/posts/:id) 하위에서만 쓰이므로 스코프 안 경로로 고정한다.
vi.mock("next/navigation", () => ({
  usePathname: () => "/community/posts/1",
}));

const revalidateCommunityPost = vi.fn().mockResolvedValue(undefined);
vi.mock("@/lib/community-revalidate", () => ({
  revalidateCommunityPost: (postId: number) => revalidateCommunityPost(postId),
}));

function renderCommentSection(postId: number) {
  return render(
    <CommunitySessionProvider>
      <CommentSection postId={postId} />
    </CommunitySessionProvider>
  );
}

const SESSION = { loggedIn: true, userId: 1, nickname: "글쓴이", activeProviders: ["google", "naver"] };

const COMMENTS = {
  topLevel: [
    {
      id: 10,
      postId: 1,
      parentId: null,
      ownerId: 1,
      authorNickname: "글쓴이",
      content: "최상위 댓글",
      deleted: false,
      createdAt: "2026-01-01T00:00:00Z",
      targetPage: null,
      replies: [
        {
          id: 11,
          postId: 1,
          parentId: 10,
          ownerId: 2,
          authorNickname: "다른사람",
          content: "답글",
          deleted: false,
          createdAt: "2026-01-01T00:01:00Z",
          targetPage: null,
          replies: [],
        },
      ],
    },
    {
      id: 12,
      postId: 1,
      parentId: null,
      ownerId: null,
      authorNickname: "(삭제됨)",
      content: "삭제된 댓글입니다.",
      deleted: true,
      createdAt: "2026-01-01T00:02:00Z",
      targetPage: null,
      replies: [],
    },
  ],
  totalTopLevelComments: 2,
  page: 0,
  size: 50,
  totalPages: 1,
};

function mockFetch() {
  return vi.fn().mockImplementation((url: string) => {
    if (url.includes("/session")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
    }
    if (url.includes("/comments")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(COMMENTS) });
    }
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
  });
}

describe("커뮤니티 댓글 섹션", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("최상위 댓글에만 답글 버튼을 보여주고 답글에는 보여주지 않는다", async () => {
    global.fetch = mockFetch();

    renderCommentSection(1);

    await waitFor(() => {
      expect(screen.getByText("최상위 댓글")).toBeInTheDocument();
    });

    const replyButtons = screen.getAllByRole("button", { name: "답글" });
    expect(replyButtons).toHaveLength(1);
  });

  it("답글은 부모 댓글 아래에 중첩되어 보인다", async () => {
    global.fetch = mockFetch();

    renderCommentSection(1);

    await waitFor(() => {
      expect(screen.getByText("다른사람")).toBeInTheDocument();
    });

    const topLevelItem = screen.getByText("최상위 댓글").closest("li");
    expect(topLevelItem).not.toBeNull();
    expect(topLevelItem!.querySelector(".community-comment-replies")).not.toBeNull();
  });

  it("삭제된 댓글은 마스킹된 문구만 보여주고 답글·삭제 버튼을 숨긴다", async () => {
    global.fetch = mockFetch();

    renderCommentSection(1);

    await waitFor(() => {
      expect(screen.getByText("삭제된 댓글입니다.")).toBeInTheDocument();
    });

    const deletedItem = screen.getByText("삭제된 댓글입니다.").closest("li");
    expect(deletedItem).not.toBeNull();
    expect(deletedItem!.querySelector("button")).toBeNull();
  });

  it("댓글 헤더는 상위 댓글 총 개수를 보여준다(답글 제외)", async () => {
    global.fetch = mockFetch();

    renderCommentSection(1);

    await waitFor(() => {
      expect(screen.getByText("댓글 2개")).toBeInTheDocument();
    });
  });

  it("로그인하지 않은 사용자에게는 댓글 작성 폼 대신 로그인 안내 문구를 보여준다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/session")) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ loggedIn: false, userId: null, nickname: null, activeProviders: [] }),
        });
      }
      if (url.includes("/comments")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(COMMENTS) });
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });

    renderCommentSection(1);

    await waitFor(() => {
      expect(screen.getByText("댓글을 작성하려면 로그인이 필요합니다.")).toBeInTheDocument();
    });
    expect(screen.queryByLabelText("댓글 작성")).not.toBeInTheDocument();
    expect(screen.queryAllByRole("button", { name: "답글" })).toHaveLength(0);
  });

  it("내용이 비어 있으면 등록 버튼이 비활성화되고, 입력하면 활성화된다", async () => {
    global.fetch = mockFetch();

    renderCommentSection(1);

    const submitButton = await screen.findByRole("button", { name: "등록" });
    expect(submitButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText("댓글 작성"), { target: { value: "새 댓글" } });
    expect(submitButton).toBeEnabled();

    fireEvent.change(screen.getByLabelText("댓글 작성"), { target: { value: "   " } });
    expect(submitButton).toBeDisabled();
  });

  it("댓글을 작성하면 입력값을 비우고 목록을 새로고침한다", async () => {
    let commentsCallCount = 0;
    let createBody: unknown = null;
    global.fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url.includes("/session")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
      }
      if (init?.method === "POST" && url.includes("/comments")) {
        createBody = JSON.parse(init.body as string);
        return Promise.resolve({
          ok: true,
          status: 201,
          json: () => Promise.resolve({ id: 20, targetPage: 0 }),
        });
      }
      if (url.includes("/comments")) {
        commentsCallCount++;
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(COMMENTS) });
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });

    renderCommentSection(1);

    fireEvent.change(await screen.findByLabelText("댓글 작성"), { target: { value: "새 댓글 내용" } });
    fireEvent.click(screen.getByRole("button", { name: "등록" }));

    await waitFor(() => {
      expect(screen.getByLabelText("댓글 작성")).toHaveValue("");
    });
    expect(createBody).toEqual({ content: "새 댓글 내용", parentId: null });
    // 초기 로드 1회 + 작성 후 재조회 1회
    expect(commentsCallCount).toBe(2);
    // 목록 페이지의 commentCount 캐시(30초 ISR)를 즉시 무효화해 다음 방문 시
    // 낡은 댓글 수를 보여주지 않는다.
    await waitFor(() => {
      expect(revalidateCommunityPost).toHaveBeenCalledWith(1);
    });
  });

  it("댓글 작성에 실패하면 오류 메시지를 보여주고 입력값을 지우지 않는다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url.includes("/session")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
      }
      if (init?.method === "POST" && url.includes("/comments")) {
        return Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) });
      }
      if (url.includes("/comments")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(COMMENTS) });
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });

    renderCommentSection(1);

    fireEvent.change(await screen.findByLabelText("댓글 작성"), { target: { value: "실패할 댓글" } });
    fireEvent.click(screen.getByRole("button", { name: "등록" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("댓글 작성에 실패했습니다.");
    });
    expect(screen.getByLabelText("댓글 작성")).toHaveValue("실패할 댓글");
  });

  it("답글 버튼을 누르면 답글 작성 중 배너가 뜨고, 취소하면 사라진다", async () => {
    global.fetch = mockFetch();

    renderCommentSection(1);

    fireEvent.click(await screen.findByRole("button", { name: "답글" }));
    expect(screen.getByText("답글 작성 중")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("글쓴이님에게 답글을 작성합니다.");

    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.queryByText("답글 작성 중")).not.toBeInTheDocument();
  });

  it("본인 댓글의 삭제 버튼을 누르면 확인 후 삭제 요청을 보내고 목록을 새로고침한다", async () => {
    let commentsCallCount = 0;
    let deletedId: number | null = null;
    global.fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url.includes("/session")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
      }
      if (init?.method === "DELETE") {
        deletedId = Number(url.split("/").pop());
        return Promise.resolve({ ok: true, status: 204, json: () => Promise.resolve(null) });
      }
      if (url.includes("/comments")) {
        commentsCallCount++;
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(COMMENTS) });
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });

    renderCommentSection(1);

    fireEvent.click(await screen.findByRole("button", { name: "삭제" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }));

    await waitFor(() => {
      expect(commentsCallCount).toBe(2);
    });
    expect(deletedId).toBe(10);
  });

  it("삭제 확인 창에서 취소하면 삭제 요청을 보내지 않는다", async () => {
    const fetchSpy = mockFetch();
    global.fetch = fetchSpy;

    renderCommentSection(1);

    fireEvent.click(await screen.findByRole("button", { name: "삭제" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "취소" }));

    expect(fetchSpy).not.toHaveBeenCalledWith(
      expect.stringContaining("/community/comments/"),
      expect.anything()
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("댓글 삭제에 실패하면 오류 메시지를 보여준다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url.includes("/session")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
      }
      if (init?.method === "DELETE") {
        return Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) });
      }
      if (url.includes("/comments")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(COMMENTS) });
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });

    renderCommentSection(1);

    fireEvent.click(await screen.findByRole("button", { name: "삭제" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("댓글 삭제에 실패했습니다.");
    });
  });

  it("페이지가 여러 개면 이전/다음 버튼으로 다른 페이지를 불러온다", async () => {
    const requestedPages: number[] = [];
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/session")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
      }
      if (url.includes("/comments")) {
        const page = Number(new URL(url, "http://localhost").searchParams.get("page"));
        requestedPages.push(page);
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ ...COMMENTS, page, totalPages: 3 }),
        });
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });

    renderCommentSection(1);

    await screen.findByText("1 / 3");
    expect(screen.getByRole("button", { name: "이전" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    await waitFor(() => {
      expect(screen.getByText("2 / 3")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "이전" })).toBeEnabled();

    expect(requestedPages).toEqual([0, 1]);
  });

  it("댓글 목록 조회가 실패하면 백엔드가 준 오류 메시지를 보여준다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/session")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
      }
      if (url.includes("/comments")) {
        return Promise.resolve({
          ok: false,
          status: 500,
          json: () => Promise.resolve({ message: "일시적인 서버 오류입니다." }),
        });
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });

    renderCommentSection(1);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("일시적인 서버 오류입니다.");
    });
  });

  it("댓글 목록 조회 중 네트워크 오류가 나면 일반 오류 문구로 대체한다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/session")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
      }
      if (url.includes("/comments")) {
        return Promise.reject(new Error("network down"));
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });

    renderCommentSection(1);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("댓글을 불러오지 못했습니다.");
    });
  });

  // F-04: 이 컴포넌트의 로딩 useEffect는 [postId]에만 반응하도록 exhaustive-deps를
  // 의도적으로 억제한다(loadComments는 매 렌더 재생성되는 클로저라 deps에 넣으면 무한
  // 재조회 루프가 된다). postId가 그대로인 리렌더링에서는 다시 fetch하지 않아야
  // 이 억제가 안전하다는 것을 증명한다.
  it("postId가 바뀌지 않으면 리렌더링돼도 댓글을 다시 불러오지 않는다(F-04 exhaustive-deps 억제 근거)", async () => {
    let commentsCallCount = 0;
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/session")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
      }
      if (url.includes("/comments")) {
        commentsCallCount++;
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(COMMENTS) });
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });

    const { rerender } = renderCommentSection(1);
    await screen.findByText("최상위 댓글");
    expect(commentsCallCount).toBe(1);

    rerender(
      <CommunitySessionProvider>
        <CommentSection postId={1} />
      </CommunitySessionProvider>
    );

    // 리렌더링 직후에도 재조회가 없어야 한다 — 있다면 억제가 감추고 있던 루프 회귀다.
    expect(commentsCallCount).toBe(1);
  });

  // FE-063: /info/community-guidelines가 "게시글·댓글 옆의 신고 버튼"을 안내하는데 실제로는
  // 게시글만 신고할 수 있었다. 댓글·답글에도 신고 진입을 제공한다.
  it("남의 댓글과 답글에는 신고 버튼을 보여준다", async () => {
    global.fetch = mockFetch();

    renderCommentSection(1);

    await waitFor(() => {
      expect(screen.getByText("다른사람")).toBeInTheDocument();
    });

    // 답글(id 11, ownerId 2)은 세션 사용자(userId 1)의 것이 아니므로 신고할 수 있다.
    const replyItem = screen.getByText("다른사람").closest("li");
    expect(replyItem).not.toBeNull();
    const replyActionLabels = Array.from(replyItem!.querySelectorAll("button")).map((b) => b.textContent);
    expect(replyActionLabels).toContain("신고");
  });

  it("본인 댓글에는 삭제만 있고 신고 버튼이 없다", async () => {
    global.fetch = mockFetch();

    renderCommentSection(1);

    await waitFor(() => {
      expect(screen.getByText("최상위 댓글")).toBeInTheDocument();
    });

    // 최상위 댓글(id 10)은 ownerId 1 = 세션 사용자 본인이다.
    const ownItem = screen.getByText("최상위 댓글").closest("li");
    expect(ownItem).not.toBeNull();
    const ownActions = ownItem!.querySelector(".community-comment-actions");
    expect(ownActions).not.toBeNull();
    const ownActionLabels = Array.from(ownActions!.querySelectorAll("button")).map((b) => b.textContent);
    expect(ownActionLabels).toContain("삭제");
    expect(ownActionLabels).not.toContain("신고");
  });

  it("삭제된 댓글에는 신고 버튼을 보여주지 않는다", async () => {
    global.fetch = mockFetch();

    renderCommentSection(1);

    await waitFor(() => {
      expect(screen.getByText("삭제된 댓글입니다.")).toBeInTheDocument();
    });

    const deletedItem = screen.getByText("삭제된 댓글입니다.").closest("li");
    expect(deletedItem!.querySelector("button")).toBeNull();
  });
});

// C-1: 차단은 저장·조회만 될 뿐 렌더링에 적용되지 않았다 — 실제로 걸러지는지 검증한다.
describe("커뮤니티 댓글 섹션의 차단 필터링(C-1)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  function renderWithBlockedUsers(postId: number, blockedUserIds: number[]) {
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("/session")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
      }
      if (url.includes("/blocked-users")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(blockedUserIds) });
      }
      if (url.includes("/comments")) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(COMMENTS) });
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
    });
    return render(
      <CommunitySessionProvider>
        <BlockedUsersProvider>
          <CommentSection postId={postId} />
        </BlockedUsersProvider>
      </CommunitySessionProvider>
    );
  }

  it("차단한 사용자의 답글은 걸러지고 최상위 댓글은 그대로 보인다", async () => {
    renderWithBlockedUsers(1, [2]);

    // 댓글 목록 조회와 차단 목록 조회는 서로 독립된 fetch라 완료 순서가 보장되지 않는다 —
    // 최종적으로 걸러지는 조건 자체를 폴링해야 한다("최상위 댓글"이 이미 보인다"는 사실은
    // 차단 목록 반영 여부와 무관하게 먼저 참일 수 있다).
    await waitFor(() => {
      expect(screen.queryByText("다른사람")).not.toBeInTheDocument();
    });
    expect(screen.getByText("최상위 댓글")).toBeInTheDocument();
  });

  it("최상위 댓글 작성자를 차단하면 그 댓글 자체가 걸러진다", async () => {
    renderWithBlockedUsers(1, [1]);

    await waitFor(() => {
      expect(screen.queryByText("최상위 댓글")).not.toBeInTheDocument();
    });
    // ownerId가 null인 삭제된 댓글은 차단 대상이 될 수 없으므로 그대로 남는다.
    expect(screen.getByText("삭제된 댓글입니다.")).toBeInTheDocument();
  });
});
