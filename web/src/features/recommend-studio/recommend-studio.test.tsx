import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SessionContext, type SessionState } from "@/entities/user-session/session-context";

import { RecommendStudio } from "./recommend-studio";

const recommendNumbers = vi.fn();
const recommendNumbersForAccount = vi.fn();
const saveNumbersToAccount = vi.fn();
const saveNumbersToDevice = vi.fn();

vi.mock("@/entities/recommendation/api", () => ({
  recommendNumbers: (...args: unknown[]) => recommendNumbers(...args),
  recommendNumbersForAccount: (...args: unknown[]) => recommendNumbersForAccount(...args),
}));

vi.mock("@/entities/saved-number/api", () => ({
  saveNumbersToAccount: (...args: unknown[]) => saveNumbersToAccount(...args),
  saveNumbersToDevice: (...args: unknown[]) => saveNumbersToDevice(...args),
}));

const ANONYMOUS: SessionState = {
  session: { loggedIn: false, userId: null, nickname: null, activeProviders: [] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

function response(numbers: number[][]) {
  return {
    recommendations: numbers,
    strategy: "balanced" as const,
    algorithmVersion: "v1",
    historyThroughRound: 1150,
    historicalExclusionApplied: false,
    exclusionPolicyVersion: "v1",
    setId: null,
    items: null,
    createdAt: null,
  };
}

function renderStudio() {
  return render(
    <SessionContext.Provider value={ANONYMOUS}>
      <RecommendStudio />
    </SessionContext.Provider>,
  );
}

beforeEach(() => {
  recommendNumbers.mockReset();
  recommendNumbersForAccount.mockReset();
  saveNumbersToAccount.mockReset();
  saveNumbersToDevice.mockReset();
});

describe("추천 스튜디오 — 결과 목록", () => {
  // KF-26(FE-OPT-43, docs/improvement.md): key가 numbers.join("-")(값 기반)인데
  // saveOutcomes는 index로 조회해, 중복 조합이 섞이면 "저장했습니다" 배지가
  // 엉뚱한 행에 붙을 수 있었다.
  it("중복 조합이 섞여도 저장한 행에만 저장 배지가 붙는다", async () => {
    const user = userEvent.setup();
    const duplicate = [1, 2, 3, 4, 5, 6];
    recommendNumbers.mockResolvedValue(response([duplicate, duplicate]));
    saveNumbersToDevice.mockResolvedValue({ created: true });

    renderStudio();
    await user.click(screen.getByRole("button", { name: "조합 만들기" }));

    const saveButtons = await screen.findAllByRole("button", { name: "보관함에 저장" });
    expect(saveButtons).toHaveLength(2);

    await user.click(saveButtons[0]!);

    const badges = await screen.findAllByText("저장했습니다.");
    expect(badges).toHaveLength(1);

    // 첫 번째 행에만 배지가 붙어야 한다 — 두 번째 행의 저장 버튼 근처에는 없어야 함.
    const rows = screen.getAllByRole("listitem");
    expect(rows[0]).toHaveTextContent("저장했습니다.");
    expect(rows[1]).not.toHaveTextContent("저장했습니다.");
  });
});
