import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { CommentSection } from "@/components/community/comment-section";
import { CommunitySessionProvider } from "@/components/community/community-session-provider";

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
});
