import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Pagination } from "./surface";

const buildHref = (page: number) => `/community?page=${page}`;

describe("Pagination — 처음·이전·다음·마지막 (레거시 FE-052)", () => {
  it("페이지가 1개뿐이면 아무것도 렌더하지 않는다", () => {
    const { container } = render(<Pagination page={0} totalPages={1} buildHref={buildHref} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("첫 페이지에서는 처음·이전이 비활성 표시고, 다음 페이지로 이동한다", () => {
    render(<Pagination page={0} totalPages={5} buildHref={buildHref} />);

    expect(screen.getByRole("link", { name: "처음" })).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("link", { name: "이전" })).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("link", { name: "다음" })).toHaveAttribute("href", "/community?page=1");
    expect(screen.getByRole("link", { name: "마지막" })).toHaveAttribute(
      "href",
      "/community?page=4",
    );
    expect(screen.getByText("1 / 5")).toBeInTheDocument();
  });

  it("가운데 페이지에서는 네 링크 모두 활성화돼 있고 올바른 페이지를 가리킨다", () => {
    render(<Pagination page={2} totalPages={5} buildHref={buildHref} />);

    expect(screen.getByRole("link", { name: "처음" })).not.toHaveAttribute("aria-disabled");
    expect(screen.getByRole("link", { name: "처음" })).toHaveAttribute("href", "/community?page=0");
    expect(screen.getByRole("link", { name: "이전" })).toHaveAttribute("href", "/community?page=1");
    expect(screen.getByRole("link", { name: "다음" })).toHaveAttribute("href", "/community?page=3");
    expect(screen.getByRole("link", { name: "마지막" })).not.toHaveAttribute("aria-disabled");
    expect(screen.getByText("3 / 5")).toBeInTheDocument();
  });

  it("마지막 페이지에서는 다음·마지막이 비활성 표시다", () => {
    render(<Pagination page={4} totalPages={5} buildHref={buildHref} />);

    expect(screen.getByRole("link", { name: "다음" })).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("link", { name: "마지막" })).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("link", { name: "이전" })).toHaveAttribute("href", "/community?page=3");
  });
});
