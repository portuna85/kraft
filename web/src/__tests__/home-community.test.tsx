import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { HomeCommunity } from "@/app/home-sections";
import type { HomeCommunityPostSummary } from "@/lib/api";

function post(id: number, title: string): HomeCommunityPostSummary {
  return {
    id,
    title,
    authorNameSnapshot: "테스터",
    createdAt: "2026-07-01T12:00:00Z",
  };
}

describe("홈 커뮤니티 섹션", () => {
  it("최근 글과 인기 글이 같으면 제목을 중복 렌더링하지 않는다", () => {
    const sharedPosts = [post(1, "같은 게시글")];

    render(<HomeCommunity latestPosts={sharedPosts} popularPosts={sharedPosts} unavailable={false} />);

    expect(screen.getAllByRole("link", { name: /같은 게시글/ })).toHaveLength(1);
    expect(screen.getByText("최근 글과 이번 주 인기 글이 같아 한 번만 표시합니다.")).toBeInTheDocument();
  });

  it("첫 최근 글은 게시 시각과 무관하게 NEW가 아닌 최신 글로 안내한다", () => {
    render(<HomeCommunity latestPosts={[post(1, "오래된 최신 글")]} popularPosts={[]} unavailable={false} />);

    expect(screen.getByText("● 최신")).toBeInTheDocument();
    expect(screen.queryByText(/NEW/)).not.toBeInTheDocument();
  });

  it("최근 글과 인기 글이 다르면 각각 한 번씩 표시한다", () => {
    render(
      <HomeCommunity
        latestPosts={[post(1, "최근 게시글")]}
        popularPosts={[post(2, "인기 게시글")]}
        unavailable={false}
      />,
    );

    expect(screen.getByRole("link", { name: /최근 게시글/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "인기 게시글" })).toBeInTheDocument();
  });

  it("인기 상위 글이 최신 글 표시 범위 밖이면 인기 목록을 유지한다", () => {
    render(
      <HomeCommunity
        latestPosts={[
          post(1, "최신 1"),
          post(2, "최신 2"),
          post(3, "최신 3"),
          post(4, "최신 4"),
          post(5, "주간 인기 1위"),
        ]}
        popularPosts={[
          post(5, "주간 인기 1위"),
          post(1, "최신 1"),
          post(2, "최신 2"),
          post(3, "최신 3"),
          post(4, "최신 4"),
        ]}
        unavailable={false}
      />,
    );

    expect(screen.getByRole("link", { name: "주간 인기 1위" })).toBeInTheDocument();
    expect(screen.queryByText("최근 글과 이번 주 인기 글이 같아 한 번만 표시합니다.")).not.toBeInTheDocument();
  });
});
