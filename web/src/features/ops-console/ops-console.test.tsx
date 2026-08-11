import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/shared/api/error";

import { OpsConsole } from "./ops-console";

const { getOpsSummary, collectLatest, collectRound } = vi.hoisted(() => ({
  getOpsSummary: vi.fn(),
  collectLatest: vi.fn(),
  collectRound: vi.fn(),
}));

vi.mock("@/entities/ops/api", () => ({ getOpsSummary, collectLatest, collectRound }));

function summary(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    service: "kraft-lotto",
    timezone: "Asia/Seoul",
    status: "OK",
    latestRound: 1150,
    latestDrawDate: "2025-01-04",
    checkedAt: "2025-01-05T00:00:00Z",
    fresh: true,
    ...overrides,
  };
}

function winningNumber(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    round: 1151,
    drawDate: "2025-01-11",
    numbers: [1, 2, 3, 4, 5, 6],
    bonusNumber: 7,
    firstPrizeAmount: 2_000_000_000,
    secondPrize: 0,
    secondWinners: 0,
    totalSales: 0,
    firstAccumAmount: 0,
    ...overrides,
  };
}

afterEach(() => {
  vi.clearAllMocks();
});

async function enterToken(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("운영 토큰"), "secret-token");
}

describe("OpsConsole", () => {
  it("토큰이 비어 있으면 액션 버튼이 비활성 상태다", () => {
    render(<OpsConsole />);

    expect(screen.getByRole("button", { name: "운영 상태 확인" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "최신 회차 반영" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "지정 회차 반영" })).toBeDisabled();
  });

  it("상태 확인이 성공하면 요약을 보여준다", async () => {
    const user = userEvent.setup();
    getOpsSummary.mockResolvedValue(summary());
    render(<OpsConsole />);

    await enterToken(user);
    await user.click(screen.getByRole("button", { name: "운영 상태 확인" }));

    expect(await screen.findByText("kraft-lotto")).toBeInTheDocument();
    expect(screen.getByText("1150회")).toBeInTheDocument();
  });

  it("상태 확인이 실패하면 오류 메시지를 보여준다", async () => {
    const user = userEvent.setup();
    getOpsSummary.mockRejectedValue(
      new ApiError("client", "토큰이 올바르지 않습니다.", { status: 401 }),
    );
    render(<OpsConsole />);

    await enterToken(user);
    await user.click(screen.getByRole("button", { name: "운영 상태 확인" }));

    expect(await screen.findByText("토큰이 올바르지 않습니다.")).toBeInTheDocument();
  });

  it("정수가 아닌 회차는 요청을 보내지 않는다", async () => {
    const user = userEvent.setup();
    render(<OpsConsole />);

    await enterToken(user);
    await user.type(screen.getByLabelText("지정 회차"), "0");
    await user.click(screen.getByRole("button", { name: "지정 회차 반영" }));

    expect(await screen.findByText("1 이상의 정수를 입력해 주세요.")).toBeInTheDocument();
    expect(collectRound).not.toHaveBeenCalled();
  });

  it("지정 회차 반영이 성공하면 성공 메시지를 보여주고 요약을 다시 조회한다", async () => {
    const user = userEvent.setup();
    collectRound.mockResolvedValue(winningNumber());
    getOpsSummary.mockResolvedValue(summary({ latestRound: 1151 }));
    render(<OpsConsole />);

    await enterToken(user);
    await user.type(screen.getByLabelText("지정 회차"), "1151");
    await user.click(screen.getByRole("button", { name: "지정 회차 반영" }));

    expect(await screen.findByText(/1151회 반영 완료/)).toBeInTheDocument();
    await waitFor(() => expect(getOpsSummary).toHaveBeenCalledWith("secret-token"));
    expect(await screen.findByText("1151회")).toBeInTheDocument();
  });

  it("최신 회차 반영 실패 시 오류 메시지를 보여준다", async () => {
    const user = userEvent.setup();
    collectLatest.mockRejectedValue(
      new ApiError("server", "운영 기능이 꺼져 있습니다.", { status: 503 }),
    );
    render(<OpsConsole />);

    await enterToken(user);
    await user.click(screen.getByRole("button", { name: "최신 회차 반영" }));

    expect(await screen.findByText("운영 기능이 꺼져 있습니다.")).toBeInTheDocument();
  });

  it("반영 성공 후 요약 재조회가 실패해도 성공 메시지는 유지한다 (FE-077)", async () => {
    const user = userEvent.setup();
    collectLatest.mockResolvedValue(winningNumber({ round: 1151 }));
    getOpsSummary.mockRejectedValue(new ApiError("server", "boom", { status: 500 }));
    render(<OpsConsole />);

    await enterToken(user);
    await user.click(screen.getByRole("button", { name: "최신 회차 반영" }));

    expect(await screen.findByText(/1151회 반영 완료/)).toBeInTheDocument();
    expect(
      await screen.findByText(
        "방금 반영됐지만 요약을 다시 불러오지 못했습니다. 상태 확인을 다시 눌러 주세요.",
      ),
    ).toBeInTheDocument();
  });
});
