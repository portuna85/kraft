import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Badge, StatusBadge } from "./badge";
import { Divider, EmptyState, ErrorState, InlineAlert, Skeleton } from "./states";

describe("EmptyState", () => {
  it("빈 이유를 표시 대상에 남긴다 — 필터 결과와 원래 빈 상태를 구분하기 위함", () => {
    const { rerender, container } = render(
      <EmptyState reason="no-data" title="아직 글이 없습니다" description="첫 글을 써 보세요." />,
    );
    expect(container.querySelector('[data-reason="no-data"]')).toBeInTheDocument();

    rerender(
      <EmptyState
        reason="filtered"
        title="조건에 맞는 글이 없습니다"
        description="검색어나 카테고리를 바꿔 보세요."
      />,
    );
    expect(container.querySelector('[data-reason="filtered"]')).toBeInTheDocument();
  });

  it("두 원인이 서로 다른 문구를 갖는다", () => {
    const { rerender } = render(
      <EmptyState reason="no-data" title="아직 글이 없습니다" description="첫 글을 써 보세요." />,
    );
    expect(screen.getByText("아직 글이 없습니다")).toBeInTheDocument();

    rerender(
      <EmptyState
        reason="filtered"
        title="조건에 맞는 글이 없습니다"
        description="검색어를 바꿔 보세요."
      />,
    );
    expect(screen.queryByText("아직 글이 없습니다")).not.toBeInTheDocument();
    expect(screen.getByText("조건에 맞는 글이 없습니다")).toBeInTheDocument();
  });
});

describe("ErrorState", () => {
  it("복구 액션을 함께 렌더한다", () => {
    render(
      <ErrorState
        title="불러오지 못했습니다"
        description="잠시 후 다시 시도해 주세요."
        action={<button type="button">다시 시도</button>}
      />,
    );
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("오류로 읽히도록 alert 역할을 갖는다", () => {
    render(
      <ErrorState
        title="불러오지 못했습니다"
        description="잠시 후 다시 시도해 주세요."
        action={<button type="button">다시 시도</button>}
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("불러오지 못했습니다");
  });
});

describe("Badge", () => {
  it("라벨 텍스트를 반드시 노출한다", () => {
    render(<Badge tone="warning" label="점검 중" />);
    expect(screen.getByText("점검 중")).toBeInTheDocument();
  });
});

describe("StatusBadge", () => {
  it("색상 점이 아니라 텍스트로 상태를 전달한다", () => {
    const { container } = render(<StatusBadge status="stale" label="갱신 지연" />);

    expect(screen.getByText("갱신 지연")).toBeInTheDocument();
    // 점은 장식이므로 보조 기술에서 숨겨져 있어야 한다.
    expect(container.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
  });
});

describe("InlineAlert", () => {
  it("위험 알림은 즉시 읽히는 alert 역할을 쓴다", () => {
    render(<InlineAlert tone="danger">삭제하지 못했습니다.</InlineAlert>);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("그 외 알림은 작업을 끊지 않는 status 역할을 쓴다", () => {
    render(<InlineAlert tone="success">저장했습니다.</InlineAlert>);
    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});

describe("Skeleton", () => {
  it("보조 기술에서 숨겨진다", () => {
    const { container } = render(<Skeleton />);
    expect(container.firstElementChild).toHaveAttribute("aria-hidden", "true");
  });
});

describe("Divider", () => {
  it("장식용일 때 보조 기술에서 숨겨진다", () => {
    const { container } = render(<Divider />);
    expect(container.querySelector("hr")).toHaveAttribute("aria-hidden", "true");
  });
});
