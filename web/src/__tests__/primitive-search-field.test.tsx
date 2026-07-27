import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { SearchField } from "@/ui/primitives/search-field";

describe("SearchField 프리미티브", () => {
  it("minLength 미만이면 제출해도 onSubmit이 호출되지 않는다", () => {
    const onSubmit = vi.fn();
    render(
      <SearchField id="q" minLength={2} maxLength={50} value="a" onChange={() => {}} onSubmit={onSubmit} />
    );
    screen.getByRole("button", { name: "검색" }).click();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("minLength 이상이면 onSubmit(value)을 호출한다", () => {
    const onSubmit = vi.fn();
    render(
      <SearchField id="q" minLength={2} maxLength={50} value="로또" onChange={() => {}} onSubmit={onSubmit} />
    );
    screen.getByRole("button", { name: "검색" }).click();
    expect(onSubmit).toHaveBeenCalledWith("로또");
  });

  it("입력 필드는 접근 가능한 이름을 갖는다", () => {
    render(<SearchField id="q" minLength={2} maxLength={50} value="" onChange={() => {}} onSubmit={() => {}} />);
    expect(screen.getByRole("searchbox")).toBeInTheDocument();
  });
});
