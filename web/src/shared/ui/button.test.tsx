import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { Button, IconButton, LinkButton } from "./button";

describe("Button", () => {
  it("라벨을 그대로 렌더한다", () => {
    render(<Button>저장</Button>);
    expect(screen.getByRole("button", { name: "저장" })).toBeInTheDocument();
  });

  it("최소 44px 터치 타깃을 갖는 클래스를 적용한다", () => {
    render(<Button>저장</Button>);
    // 값 자체는 토큰(--target-min)이 소유하므로 여기서는 계약 클래스 적용만 확인한다.
    expect(screen.getByRole("button", { name: "저장" }).className).toContain("button");
  });

  it("로딩 중에는 loadingLabel을 접근 이름으로 노출한다", () => {
    render(
      <Button loading loadingLabel="저장 중">
        저장
      </Button>,
    );
    expect(screen.getByRole("button", { name: "저장 중" })).toBeInTheDocument();
  });

  it("로딩 중에는 스스로 비활성화해 중복 제출을 막는다", () => {
    render(
      <Button loading loadingLabel="저장 중">
        저장
      </Button>,
    );
    const button = screen.getByRole("button", { name: "저장 중" });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");
  });

  it("비활성 상태에서는 클릭 핸들러가 호출되지 않는다", async () => {
    const onClick = vi.fn();
    const { default: userEvent } = await import("@testing-library/user-event");
    render(
      <Button disabled onClick={onClick}>
        저장
      </Button>,
    );

    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(onClick).not.toHaveBeenCalled();
  });

  it("dangerQuiet 변형도 일반 버튼처럼 클릭 가능하다", async () => {
    const onClick = vi.fn();
    const { default: userEvent } = await import("@testing-library/user-event");
    render(
      <Button variant="dangerQuiet" onClick={onClick}>
        신고
      </Button>,
    );

    await userEvent.click(screen.getByRole("button", { name: "신고" }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});

describe("LinkButton", () => {
  it("버튼처럼 보이는 링크를 렌더한다", () => {
    render(<LinkButton href="/recommend">번호 추천 받기</LinkButton>);

    const link = screen.getByRole("link", { name: "번호 추천 받기" });
    expect(link).toHaveAttribute("href", "/recommend");
  });
});

describe("IconButton", () => {
  it("aria-label을 접근 이름으로 쓴다", () => {
    render(<IconButton aria-label="닫기" icon={<svg />} />);
    expect(screen.getByRole("button", { name: "닫기" })).toBeInTheDocument();
  });

  it("아이콘 자체는 보조 기술에서 숨긴다", () => {
    const { container } = render(<IconButton aria-label="닫기" icon={<svg data-testid="i" />} />);
    expect(container.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
  });

  it("로딩 중에도 접근 이름을 유지한다", () => {
    render(<IconButton aria-label="닫기" icon={<svg />} loading loadingLabel="닫는 중" />);
    expect(screen.getByRole("button", { name: "닫는 중" })).toBeInTheDocument();
  });
});
