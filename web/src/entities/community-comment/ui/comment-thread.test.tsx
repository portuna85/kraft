import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { CommunityComment } from "../schema";
import { CommentThread } from "./comment-thread";

function comment(overrides: Partial<CommunityComment> & { id: number }): CommunityComment {
  return {
    postId: 1,
    parentId: null,
    ownerId: 10,
    authorNickname: "작성자",
    content: "내용",
    deleted: false,
    createdAt: "2026-08-01T12:00:00Z",
    targetPage: null,
    replies: [],
    ...overrides,
  };
}

describe("댓글 스레드", () => {
  it("답글을 상위 댓글 안에 중첩한다", () => {
    // 들여쓰기로만 구분하면 화면을 못 보는 사용자는 어떤 댓글의 답글인지 알 수 없다.
    render(
      <CommentThread
        comments={[
          comment({
            id: 1,
            content: "상위",
            replies: [comment({ id: 2, parentId: 1, content: "답글" })],
          }),
        ]}
      />,
    );

    const topLevel = screen.getAllByRole("listitem")[0];
    expect(topLevel).toBeDefined();
    expect(within(topLevel as HTMLElement).getByText("답글")).toBeInTheDocument();
  });

  it("삭제된 댓글도 자리를 지킨다", () => {
    // 지워 버리면 그 아래 답글이 무엇에 대한 답인지 알 수 없게 된다.
    render(
      <CommentThread
        comments={[
          comment({
            id: 1,
            deleted: true,
            content: "삭제된 댓글입니다.",
            authorNickname: "(삭제됨)",
            replies: [comment({ id: 2, parentId: 1, content: "남아 있는 답글" })],
          }),
        ]}
      />,
    );

    expect(screen.getByText("삭제된 댓글입니다.")).toBeInTheDocument();
    expect(screen.getByText("남아 있는 답글")).toBeInTheDocument();
  });

  it("삭제된 댓글에는 액션을 붙이지 않는다", () => {
    render(
      <CommentThread
        comments={[comment({ id: 1, deleted: true })]}
        renderActions={() => <button type="button">신고</button>}
      />,
    );

    // 지워진 내용에는 신고할 것도 지울 것도 없다.
    expect(screen.queryByRole("button", { name: "신고" })).not.toBeInTheDocument();
  });

  it("살아 있는 댓글에는 액션을 붙인다", () => {
    render(
      <CommentThread
        comments={[comment({ id: 1 })]}
        renderActions={() => <button type="button">신고</button>}
      />,
    );

    expect(screen.getByRole("button", { name: "신고" })).toBeInTheDocument();
  });

  it("줄바꿈을 문단으로 렌더하고 HTML로 해석하지 않는다", () => {
    render(<CommentThread comments={[comment({ id: 1, content: "첫 줄\n<b>둘째 줄</b>" })]} />);

    // 사용자가 쓴 문자열을 HTML로 해석하는 순간 XSS 경로가 열린다(§20.1).
    expect(screen.getByText("<b>둘째 줄</b>")).toBeInTheDocument();
    expect(document.querySelector("b")).toBeNull();
  });
});
