import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { PageHeader } from "@/components/page-header";

describe("PageHeader", () => {
  it("페이지의 제목, 설명과 주 행동을 하나의 header landmark로 제공한다", () => {
    render(
      <PageHeader
        eyebrow="번호 추천"
        title="추천 이력"
        description="생성한 번호 조합을 확인할 수 있습니다."
        actions={<button type="button">새 번호 만들기</button>}
      />,
    );

    expect(screen.getByRole("banner")).toHaveTextContent("번호 추천");
    expect(screen.getByRole("heading", { level: 1, name: "추천 이력" })).toBeInTheDocument();
    expect(screen.getByText("생성한 번호 조합을 확인할 수 있습니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "새 번호 만들기" })).toBeInTheDocument();
  });
});
