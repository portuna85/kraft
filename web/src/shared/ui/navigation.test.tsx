import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ComponentProps } from "react";

import { Breadcrumb } from "./navigation";

// KF-20(docs/improvement.md): prefetch={false}는 렌더된 DOM에 표준 속성으로
// 남지 않아(Link 내부 라우터 동작만 바꾼다) 렌더 결과만으로는 검증할 수 없다 —
// next/link를 목으로 바꿔 전달되는 prop을 data-prefetch로 노출한다.
vi.mock("next/link", () => ({
  default: ({
    href,
    prefetch,
    children,
    ...rest
  }: ComponentProps<"a"> & { prefetch?: boolean }) => (
    <a
      href={typeof href === "string" ? href : undefined}
      data-prefetch={String(prefetch)}
      {...rest}
    >
      {children}
    </a>
  ),
}));

describe("Breadcrumb", () => {
  it("KF-20: 상위 경로 링크가 prefetch=false다(이미 셸이 커버하는 목적지 재확인)", () => {
    render(
      <Breadcrumb
        items={[
          { label: "홈", href: "/" },
          { label: "커뮤니티", href: "/community" },
        ]}
        current="글 제목"
      />,
    );

    expect(screen.getByRole("link", { name: "홈" })).toHaveAttribute("data-prefetch", "false");
    expect(screen.getByRole("link", { name: "커뮤니티" })).toHaveAttribute(
      "data-prefetch",
      "false",
    );
  });

  it("현재 페이지는 링크가 아니라 aria-current 텍스트로 남는다", () => {
    render(<Breadcrumb items={[{ label: "홈", href: "/" }]} current="글 제목" />);

    expect(screen.queryByRole("link", { name: "글 제목" })).not.toBeInTheDocument();
    expect(screen.getByText("글 제목")).toHaveAttribute("aria-current", "page");
  });
});
