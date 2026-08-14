import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import type { CompanionPair } from "../schema";
import { CompanionPairsSection } from "./companion-pairs-section";

function pairs(count: number): CompanionPair[] {
  return Array.from({ length: count }, (_, index) => ({
    ballA: 1,
    ballB: 2 + index,
    coCount: 20 - index,
  }));
}

describe("동반 출현 쌍 목록", () => {
  it("처음엔 12개만 보여준다", () => {
    render(<CompanionPairsSection pairs={pairs(50)} totalRounds={1150} expectedLabel="17.4" />);

    expect(screen.getAllByRole("row")).toHaveLength(1 + 12); // 헤더 1 + 본문 12
    expect(
      screen.getByText("12개 쌍을 표시하고 있습니다. 배율은 쌍당 평균 17.4회 대비 값입니다."),
    ).toBeInTheDocument();
  });

  it("더 보기를 누르면 받아 둔 나머지를 전부 펼친다(추가 요청 없음)", async () => {
    const user = userEvent.setup();
    render(<CompanionPairsSection pairs={pairs(50)} totalRounds={1150} expectedLabel="17.4" />);

    await user.click(screen.getByRole("button", { name: "상위 50개 모두 보기" }));

    expect(screen.getAllByRole("row")).toHaveLength(1 + 50);
    expect(screen.queryByRole("button", { name: "상위 50개 모두 보기" })).not.toBeInTheDocument();
  });

  it("50개가 안 되게 받으면(필터 등) 더 보기 버튼을 두지 않는다", () => {
    render(<CompanionPairsSection pairs={pairs(8)} totalRounds={1150} expectedLabel="17.4" />);

    expect(screen.queryByRole("button", { name: /모두 보기/ })).not.toBeInTheDocument();
    expect(screen.getAllByRole("row")).toHaveLength(1 + 8);
  });
});
