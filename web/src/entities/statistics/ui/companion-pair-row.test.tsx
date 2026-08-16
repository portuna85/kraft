import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { CompanionPairRow } from "./companion-pair-row";

const pair = { ballA: 1, ballB: 12, coCount: 38 };

function renderRow(rank?: number) {
  return render(
    <table>
      <tbody>
        <CompanionPairRow pair={pair} totalRounds={100} rank={rank} />
      </tbody>
    </table>,
  );
}

describe("CompanionPairRow", () => {
  // I-30: 메달은 색과 "금/은/동" 텍스트로만 순위를 전달했다 — 보조기기 사용자는
  // 몇 위인지 알 방법이 없었다.
  it("1~3위는 메달에 보조기기가 읽을 수 있는 순위 라벨을 붙인다", () => {
    renderRow(1);
    expect(screen.getByLabelText("1위")).toBeInTheDocument();
  });

  // I-30: 4위 이하는 메달이 아예 렌더되지 않아 공이 왼쪽으로 당겨졌다 — 이제도
  // 같은 폭의 빈 자리를 남겨 열이 어긋나지 않는다.
  it("순위권 밖에는 메달 자리를 숨김 처리로 비워 둔다", () => {
    renderRow(4);
    expect(screen.queryByLabelText(/위$/)).not.toBeInTheDocument();
  });
});
