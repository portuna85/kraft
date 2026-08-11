import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/shared/api/error";

import { ManualEntryForm } from "./manual-entry-form";

const { upsertRound } = vi.hoisted(() => ({ upsertRound: vi.fn() }));
vi.mock("@/entities/ops/api", () => ({ upsertRound }));

afterEach(() => {
  vi.clearAllMocks();
});

function renderForm(overrides: Partial<Parameters<typeof ManualEntryForm>[0]> = {}) {
  const onStart = vi.fn();
  const onSuccess = vi.fn();
  const onError = vi.fn();
  render(
    <ManualEntryForm
      token="secret-token"
      onStart={onStart}
      onSuccess={onSuccess}
      onError={onError}
      {...overrides}
    />,
  );
  return { onStart, onSuccess, onError };
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("회차"), "1151");
  await user.type(screen.getByLabelText("추첨일"), "2025-01-11");
  await user.type(screen.getByLabelText("당첨 번호 6개"), "1, 8, 17, 24, 33, 41");
  await user.type(screen.getByLabelText("보너스 번호"), "5");
  await user.type(screen.getByLabelText("1등 당첨금"), "2000000000");
}

describe("ManualEntryForm", () => {
  it("빈 폼 제출을 막는다", async () => {
    const user = userEvent.setup();
    const { onStart } = renderForm();

    await user.click(screen.getByRole("button", { name: "적재" }));

    expect(await screen.findByText("1 이상의 정수를 입력해 주세요.")).toBeInTheDocument();
    expect(onStart).not.toHaveBeenCalled();
  });

  it("회차가 0이면 오류를 보여준다", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText("회차"), "0");
    await user.click(screen.getByRole("button", { name: "적재" }));

    expect(await screen.findByText("1 이상의 정수를 입력해 주세요.")).toBeInTheDocument();
  });

  it("번호가 5개뿐이면 오류를 보여준다", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText("당첨 번호 6개"), "1, 8, 17, 24, 33");
    await user.click(screen.getByRole("button", { name: "적재" }));

    expect(
      await screen.findByText(
        "1~45 사이의 서로 다른 정수 6개를 쉼표나 공백으로 구분해 입력해 주세요.",
      ),
    ).toBeInTheDocument();
  });

  it("번호가 범위를 벗어나면 오류를 보여준다", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText("당첨 번호 6개"), "1, 8, 17, 24, 33, 46");
    await user.click(screen.getByRole("button", { name: "적재" }));

    expect(
      await screen.findByText(
        "1~45 사이의 서로 다른 정수 6개를 쉼표나 공백으로 구분해 입력해 주세요.",
      ),
    ).toBeInTheDocument();
  });

  it("번호가 중복되면 오류를 보여준다", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText("당첨 번호 6개"), "1, 8, 17, 24, 33, 33");
    await user.click(screen.getByRole("button", { name: "적재" }));

    expect(
      await screen.findByText(
        "1~45 사이의 서로 다른 정수 6개를 쉼표나 공백으로 구분해 입력해 주세요.",
      ),
    ).toBeInTheDocument();
  });

  it("보너스 번호가 당첨 번호와 겹치면 폼 오류로 보여준다", async () => {
    const user = userEvent.setup();
    const { onStart } = renderForm();

    await fillValidForm(user);
    await user.clear(screen.getByLabelText("보너스 번호"));
    await user.type(screen.getByLabelText("보너스 번호"), "8");
    await user.click(screen.getByRole("button", { name: "적재" }));

    expect(
      await screen.findByText("보너스 번호는 당첨 번호 6개와 달라야 합니다."),
    ).toBeInTheDocument();
    expect(onStart).not.toHaveBeenCalled();
    expect(upsertRound).not.toHaveBeenCalled();
  });

  it("선택 필드를 비우면 undefined로 전송한다", async () => {
    const user = userEvent.setup();
    upsertRound.mockResolvedValue({
      round: 1151,
      drawDate: "2025-01-11",
      numbers: [1, 8, 17, 24, 33, 41],
      bonusNumber: 5,
      firstPrizeAmount: 2_000_000_000,
      secondPrize: 0,
      secondWinners: 0,
      totalSales: 0,
      firstAccumAmount: 0,
    });
    const { onSuccess } = renderForm();

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: "적재" }));

    expect(onSuccess).toHaveBeenCalled();
    expect(upsertRound).toHaveBeenCalledWith(
      "secret-token",
      expect.objectContaining({
        round: 1151,
        secondPrize: undefined,
        secondWinners: undefined,
        totalSales: undefined,
        firstAccumAmount: undefined,
      }),
    );
  });

  it("성공해도 폼 값을 지우지 않는다", async () => {
    const user = userEvent.setup();
    upsertRound.mockResolvedValue({
      round: 1151,
      drawDate: "2025-01-11",
      numbers: [1, 8, 17, 24, 33, 41],
      bonusNumber: 5,
      firstPrizeAmount: 2_000_000_000,
      secondPrize: 0,
      secondWinners: 0,
      totalSales: 0,
      firstAccumAmount: 0,
    });
    renderForm();

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: "적재" }));

    expect(await screen.findByLabelText("회차")).toHaveValue("1151");
  });

  it("서버 실패 시 값을 보존하고 폼 오류를 보여준다", async () => {
    const user = userEvent.setup();
    upsertRound.mockRejectedValue(
      new ApiError("client", "이미 존재하는 회차입니다.", { status: 409 }),
    );
    const { onError } = renderForm();

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: "적재" }));

    expect(await screen.findByText("이미 존재하는 회차입니다.")).toBeInTheDocument();
    expect(onError).toHaveBeenCalledWith("이미 존재하는 회차입니다.");
    expect(screen.getByLabelText("회차")).toHaveValue("1151");
  });

  it("무효한 필드로 제출하면 그 필드로 포커스를 옮긴다", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.click(screen.getByRole("button", { name: "적재" }));

    expect(await screen.findByLabelText("회차")).toHaveFocus();
  });
});
