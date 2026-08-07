import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { EmptyState } from "@/ui/primitives/empty-state";

describe("EmptyState 프리미티브", () => {
  it("title/description을 렌더링한다", () => {
    render(<EmptyState title="저장한 번호가 없습니다" description="추천에서 번호를 저장해 보세요" />);
    expect(screen.getByText("저장한 번호가 없습니다")).toBeInTheDocument();
    expect(screen.getByText("추천에서 번호를 저장해 보세요")).toBeInTheDocument();
  });

  it("action이 있으면 클릭 시 onClick을 호출한다", () => {
    const onClick = vi.fn();
    render(<EmptyState title="비어 있음" action={{ label: "추천받기", onClick }} />);
    screen.getByRole("button", { name: "추천받기" }).click();
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("action이 없으면 버튼을 렌더링하지 않는다", () => {
    render(<EmptyState title="비어 있음" />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
