import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import ErrorBoundary from "@/app/error";

describe("전역 오류 화면", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("오류를 콘솔에 기록하고 재시도·홈 이동 액션을 보여준다", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    const error = new Error("데이터를 가져오지 못했습니다");
    const reset = vi.fn();

    render(<ErrorBoundary error={error} reset={reset} />);

    expect(consoleError).toHaveBeenCalledWith(error);
    expect(screen.getByText("페이지를 불러오지 못했습니다")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "홈으로 이동" })).toHaveAttribute("href", "/");

    fireEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
    expect(reset).toHaveBeenCalledTimes(1);
  });

  it("digest가 있으면 안내 문구에 코드로 덧붙인다", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const error = Object.assign(new Error("실패"), { digest: "abc123" });

    render(<ErrorBoundary error={error} reset={vi.fn()} />);

    expect(screen.getByText(/코드: abc123/)).toBeInTheDocument();
  });

  it("digest가 없으면 코드 문구를 붙이지 않는다", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const error = new Error("실패");

    render(<ErrorBoundary error={error} reset={vi.fn()} />);

    expect(screen.queryByText(/코드:/)).not.toBeInTheDocument();
  });
});
