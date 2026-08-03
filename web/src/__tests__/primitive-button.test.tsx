import { createRef } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Button } from "@/ui/primitives/button";

describe("Button 프리미티브", () => {
  it("클릭 시 onClick을 호출한다", () => {
    const onClick = vi.fn();
    render(
      <Button variant="primary" onClick={onClick}>
        저장
      </Button>
    );
    screen.getByRole("button", { name: "저장" }).click();
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("disabled=true면 클릭해도 onClick이 호출되지 않는다", () => {
    const onClick = vi.fn();
    render(
      <Button variant="primary" disabled onClick={onClick}>
        저장
      </Button>
    );
    screen.getByRole("button", { name: "저장" }).click();
    expect(onClick).not.toHaveBeenCalled();
  });

  it("loading=true면 aria-busy를 세우고 loadingLabel을 스크린리더용으로 노출한다", () => {
    render(
      <Button variant="primary" loading loadingLabel="저장 중">
        저장
      </Button>
    );
    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("aria-busy", "true");
    expect(button).toBeDisabled();
    expect(screen.getByText("저장 중")).toBeInTheDocument();
  });

  it("최소 터치 영역(44px) 클래스를 기본 크기에서 보장한다", () => {
    render(<Button variant="secondary">보통</Button>);
    // jsdom은 실제 레이아웃을 계산하지 않으므로 CSS 자체를 검증하지는 못한다 —
    // 최소한 sm/lg 전용 클래스가 붙지 않은 기본(md) 상태를 확인한다.
    const button = screen.getByRole("button");
    expect(button.className).not.toMatch(/\bsm\b|\blg\b/);
  });

  // M-5: variant/size/loading 같은 구조적 계약 외의 나머지 네이티브 button 속성도
  // 그대로 통과해야 한다 — 호출부가 이런 값을 넘겨야 할 때마다 프리미티브를 우회해
  // raw <button>을 새로 만들 필요가 없게 하기 위함.
  it("네이티브 속성과 className을 그대로 전달한다", () => {
    render(
      <Button variant="primary" className="extra" data-testid="save-button" form="post-form">
        저장
      </Button>
    );
    const button = screen.getByRole("button", { name: "저장" });
    expect(button).toHaveClass("extra");
    expect(button).toHaveAttribute("data-testid", "save-button");
    expect(button).toHaveAttribute("form", "post-form");
  });

  it("ref를 실제 button DOM 노드로 전달한다", () => {
    const ref = createRef<HTMLButtonElement>();
    render(
      <Button variant="primary" ref={ref}>
        저장
      </Button>
    );
    expect(ref.current).toBeInstanceOf(HTMLButtonElement);
    expect(ref.current?.textContent).toBe("저장");
  });
});
