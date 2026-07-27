import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { InlineAlert } from "@/ui/primitives/inline-alert";

describe("InlineAlert 프리미티브", () => {
  it("danger 톤은 role=alert로 즉시 알린다", () => {
    render(<InlineAlert tone="danger" title="오류" description="다시 시도해 주세요" />);
    expect(screen.getByRole("alert")).toHaveTextContent("오류");
  });

  it("neutral/success 톤은 role=status로 렌더링한다", () => {
    render(<InlineAlert tone="success" title="완료" />);
    expect(screen.getByRole("status")).toHaveTextContent("완료");
  });

  it("description이 없으면 렌더링하지 않는다", () => {
    render(<InlineAlert tone="neutral" title="제목만" />);
    expect(screen.getByText("제목만")).toBeInTheDocument();
  });
});
