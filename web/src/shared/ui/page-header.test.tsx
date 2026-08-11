import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PageHeader } from "./page-header";

describe("PageHeader", () => {
  it("title을 h1로 렌더한다", () => {
    render(<PageHeader title="회차 운영 콘솔" />);
    expect(screen.getByRole("heading", { level: 1, name: "회차 운영 콘솔" })).toBeInTheDocument();
  });

  it("eyebrow·description·actions을 선택적으로 렌더한다", () => {
    render(
      <PageHeader
        eyebrow="내부 운영"
        title="회차 운영 콘솔"
        description="운영자 전용입니다."
        actions={<button type="button">새로고침</button>}
      />,
    );
    expect(screen.getByText("내부 운영")).toBeInTheDocument();
    expect(screen.getByText("운영자 전용입니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "새로고침" })).toBeInTheDocument();
  });

  it("eyebrow·description·actions이 없으면 렌더하지 않는다", () => {
    const { container } = render(<PageHeader title="회차 운영 콘솔" />);
    expect(container.querySelectorAll("p")).toHaveLength(0);
  });
});
