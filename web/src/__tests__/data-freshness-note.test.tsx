import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { DataFreshnessNote } from "@/components/data-freshness-note";
import type { RoundFreshness } from "@/lib/api";

describe("데이터 최신성 안내", () => {
  // FE-013: 예전에는 null이면 아무것도 렌더하지 않아, "원래 최신성 표시가 없는 화면"과
  // "조회에 실패한 화면"이 구분되지 않았다. 당첨 번호 자체는 정상이므로 오류로 키우지
  // 않되, 확인하지 못했다는 사실은 남긴다.
  it("freshness 조회에 실패하면 확인 불가 사실을 알린다", () => {
    render(<DataFreshnessNote freshness={null} />);

    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("최신성 확인 불가");
    expect(status).toHaveTextContent("표시된 회차 정보는 정상입니다");
  });

  it("최신 회차까지 반영됐으면 반영 완료 문구를 보여준다", () => {
    const freshness: RoundFreshness = {
      latestRound: 1200,
      latestDrawDate: "2026-07-18",
      fresh: true,
      checkedAt: "2026-07-21T00:00:00Z",
    };
    render(<DataFreshnessNote freshness={freshness} />);

    expect(screen.getByRole("status")).toHaveTextContent("1200회");
    expect(screen.getByRole("status")).toHaveTextContent("최신 회차까지 반영됨");
  });

  it("반영이 지연됐으면 지연 안내 문구를 보여준다", () => {
    const freshness: RoundFreshness = {
      latestRound: 1199,
      latestDrawDate: "2026-07-11",
      fresh: false,
      checkedAt: "2026-07-21T00:00:00Z",
    };
    render(<DataFreshnessNote freshness={freshness} />);

    expect(screen.getByRole("status")).toHaveTextContent("반영이 지연되고 있습니다");
  });
});
