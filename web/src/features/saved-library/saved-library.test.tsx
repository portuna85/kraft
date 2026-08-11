import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionContext, type SessionState } from "@/entities/user-session/session-context";

import { SavedLibrary } from "./saved-library";

function match(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    savedNumber: item(),
    round: 1150,
    drawDate: "2026-08-01",
    drawNumbers: [1, 8, 17, 24, 33, 41],
    bonusNumber: 5,
    matchedCount: 6,
    bonusMatch: false,
    prizeTier: "1등",
    ...overrides,
  };
}

const {
  listDeviceSavedNumbers,
  listAccountSavedNumbers,
  matchDeviceSavedNumbers,
  matchAccountSavedNumbers,
  deleteDeviceSavedNumber,
  deleteAccountSavedNumber,
} = vi.hoisted(() => ({
  listDeviceSavedNumbers: vi.fn(),
  listAccountSavedNumbers: vi.fn(),
  matchDeviceSavedNumbers: vi.fn(),
  matchAccountSavedNumbers: vi.fn(),
  deleteDeviceSavedNumber: vi.fn(),
  deleteAccountSavedNumber: vi.fn(),
}));

vi.mock("@/entities/saved-number/api", () => ({
  listDeviceSavedNumbers,
  listAccountSavedNumbers,
  matchDeviceSavedNumbers,
  matchAccountSavedNumbers,
  deleteDeviceSavedNumber,
  deleteAccountSavedNumber,
}));

const ANONYMOUS: SessionState = {
  session: { loggedIn: false, userId: null, nickname: null, activeProviders: [] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

const LOGGED_IN: SessionState = {
  session: { loggedIn: true, userId: 10, nickname: "나", activeProviders: ["google"] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

function item(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 1,
    numbers: [1, 8, 17, 24, 33, 41],
    label: null,
    source: "recommend",
    createdAt: "2026-08-01T12:00:00Z",
    ...overrides,
  };
}

function renderLibrary(session: SessionState) {
  return render(
    <SessionContext.Provider value={session}>
      <SavedLibrary latestRound={1150} />
    </SessionContext.Provider>,
  );
}

describe("보관함", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("익명은 기기 스코프 목록을 조회한다", async () => {
    listDeviceSavedNumbers.mockResolvedValue([item()]);
    renderLibrary(ANONYMOUS);

    await waitFor(() => expect(listDeviceSavedNumbers).toHaveBeenCalled());
    expect(listAccountSavedNumbers).not.toHaveBeenCalled();
  });

  it("로그인 사용자는 계정 스코프 목록을 조회한다", async () => {
    listAccountSavedNumbers.mockResolvedValue([item()]);
    renderLibrary(LOGGED_IN);

    await waitFor(() => expect(listAccountSavedNumbers).toHaveBeenCalled());
    expect(listDeviceSavedNumbers).not.toHaveBeenCalled();
  });

  it("빈 목록이면 저장 안내를 보여준다", async () => {
    listDeviceSavedNumbers.mockResolvedValue([]);
    renderLibrary(ANONYMOUS);

    expect(await screen.findByText("아직 저장한 번호가 없습니다.")).toBeInTheDocument();
  });

  it("조회 실패 시 다시 시도 버튼을 보여준다", async () => {
    listDeviceSavedNumbers.mockRejectedValue(new Error("network"));
    renderLibrary(ANONYMOUS);

    expect(await screen.findByText("보관함을 불러오지 못했습니다")).toBeInTheDocument();
  });

  it("삭제를 확인하면 해당 스코프의 삭제 API를 부른다", async () => {
    const user = userEvent.setup();
    listAccountSavedNumbers.mockResolvedValue([item()]);
    deleteAccountSavedNumber.mockResolvedValue(null);

    renderLibrary(LOGGED_IN);
    await user.click(await screen.findByText("삭제"));

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "삭제" }));

    await waitFor(() => expect(deleteAccountSavedNumber).toHaveBeenCalledWith(1));
    expect(deleteDeviceSavedNumber).not.toHaveBeenCalled();
  });

  it("익명은 대조하기를 누르면 기기 스코프로 조회해 등수를 보여준다 (§25.5)", async () => {
    const user = userEvent.setup();
    listDeviceSavedNumbers.mockResolvedValue([item()]);
    matchDeviceSavedNumbers.mockResolvedValue([match()]);

    renderLibrary(ANONYMOUS);
    await screen.findByText("삭제");
    await user.click(screen.getByRole("button", { name: "대조하기" }));

    expect(await screen.findByText("1등")).toBeInTheDocument();
    expect(matchDeviceSavedNumbers).toHaveBeenCalledWith("1150");
    expect(matchAccountSavedNumbers).not.toHaveBeenCalled();
  });

  it("로그인 사용자는 대조하기를 누르면 계정 스코프로 조회해 등수를 보여준다 (§25.5)", async () => {
    const user = userEvent.setup();
    listAccountSavedNumbers.mockResolvedValue([item()]);
    matchAccountSavedNumbers.mockResolvedValue([match({ prizeTier: "낙첨", matchedCount: 2 })]);

    renderLibrary(LOGGED_IN);
    await screen.findByText("삭제");
    await user.click(screen.getByRole("button", { name: "대조하기" }));

    expect(await screen.findByText("2개 일치")).toBeInTheDocument();
    expect(matchAccountSavedNumbers).toHaveBeenCalledWith("1150");
    expect(matchDeviceSavedNumbers).not.toHaveBeenCalled();
  });

  it("익명으로 보던 중 로그인(claim)이 끝나면 계정 스코프로 다시 조회한다 (§25.2 이관 확인)", async () => {
    listDeviceSavedNumbers.mockResolvedValue([item({ id: 1 })]);
    listAccountSavedNumbers.mockResolvedValue([item({ id: 2 })]);

    const { rerender } = render(
      <SessionContext.Provider value={ANONYMOUS}>
        <SavedLibrary latestRound={1150} />
      </SessionContext.Provider>,
    );
    await waitFor(() => expect(listDeviceSavedNumbers).toHaveBeenCalledTimes(1));
    expect(listAccountSavedNumbers).not.toHaveBeenCalled();

    // claim 성공 → identity-session이 session을 loggedIn으로 갱신 → 여기서는 그 결과로
    // 부모가 다시 렌더하는 상황을 흉내낸다. 기기 스코프로 또 조회하면 안 된다.
    rerender(
      <SessionContext.Provider value={LOGGED_IN}>
        <SavedLibrary latestRound={1150} />
      </SessionContext.Provider>,
    );

    await waitFor(() => expect(listAccountSavedNumbers).toHaveBeenCalledTimes(1));
    expect(listDeviceSavedNumbers).toHaveBeenCalledTimes(1);
  });
});
