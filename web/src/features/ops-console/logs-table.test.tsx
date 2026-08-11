import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/shared/api/error";

import { LogsTable } from "./logs-table";

const { getOpsLogs } = vi.hoisted(() => ({ getOpsLogs: vi.fn() }));
vi.mock("@/entities/ops/api", () => ({ getOpsLogs }));

afterEach(() => {
  vi.clearAllMocks();
});

function log(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 1,
    operationType: "EXTERNAL_COLLECT",
    executionStatus: "SUCCESS",
    round: 1150,
    sourceDetail: null,
    message: null,
    requestId: null,
    createdAt: "2025-01-05T00:00:00Z",
    ...overrides,
  };
}

describe("LogsTable", () => {
  it("토큰이 없으면 조회 버튼이 비활성 상태다", () => {
    render(<LogsTable token="" />);
    expect(screen.getByRole("button", { name: "이력 조회" })).toBeDisabled();
  });

  it("이력이 있으면 표로 보여준다", async () => {
    const user = userEvent.setup();
    getOpsLogs.mockResolvedValue({
      items: [log()],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    render(<LogsTable token="secret-token" />);

    await user.click(screen.getByRole("button", { name: "이력 조회" }));

    expect(await screen.findByText("자동 수집")).toBeInTheDocument();
    expect(screen.getByText("성공")).toBeInTheDocument();
    expect(screen.getByText("1150회")).toBeInTheDocument();
  });

  it("실패한 이력은 실패 배지로 보여준다", async () => {
    const user = userEvent.setup();
    getOpsLogs.mockResolvedValue({
      items: [
        log({ operationType: "MANUAL_UPSERT", executionStatus: "FAILURE", message: "검증 실패" }),
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    render(<LogsTable token="secret-token" />);

    await user.click(screen.getByRole("button", { name: "이력 조회" }));

    expect(await screen.findByText("수동 적재")).toBeInTheDocument();
    expect(screen.getByText("실패")).toBeInTheDocument();
    expect(screen.getByText("검증 실패")).toBeInTheDocument();
  });

  it("빈 목록이면 안내를 보여준다", async () => {
    const user = userEvent.setup();
    getOpsLogs.mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    render(<LogsTable token="secret-token" />);

    await user.click(screen.getByRole("button", { name: "이력 조회" }));

    expect(await screen.findByText("기록된 이력이 없습니다")).toBeInTheDocument();
  });

  it("조회 실패 시 오류 메시지를 보여준다", async () => {
    const user = userEvent.setup();
    getOpsLogs.mockRejectedValue(
      new ApiError("client", "토큰이 올바르지 않습니다.", { status: 401 }),
    );
    render(<LogsTable token="secret-token" />);

    await user.click(screen.getByRole("button", { name: "이력 조회" }));

    expect(await screen.findByText("토큰이 올바르지 않습니다.")).toBeInTheDocument();
  });

  it("다음 페이지를 누르면 다음 페이지를 다시 조회한다", async () => {
    const user = userEvent.setup();
    getOpsLogs.mockResolvedValueOnce({
      items: [log()],
      page: 0,
      size: 20,
      totalElements: 21,
      totalPages: 2,
    });
    render(<LogsTable token="secret-token" />);

    await user.click(screen.getByRole("button", { name: "이력 조회" }));
    expect(await screen.findByText("1 / 2")).toBeInTheDocument();

    getOpsLogs.mockResolvedValueOnce({
      items: [log({ id: 2, round: 1151 })],
      page: 1,
      size: 20,
      totalElements: 21,
      totalPages: 2,
    });
    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(await screen.findByText("2 / 2")).toBeInTheDocument();
    expect(getOpsLogs).toHaveBeenLastCalledWith("secret-token", { page: 1, size: 20 });
  });
});
