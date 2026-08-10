import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ReportDialog } from "./report-dialog";

const { reportContent } = vi.hoisted(() => ({
  reportContent: vi.fn(),
}));

vi.mock("../api", () => ({ reportContent }));

describe("신고 다이얼로그", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("버튼을 누르기 전에는 사유 목록이 보이지 않는다", () => {
    render(<ReportDialog targetType="POST" targetId={1} label="신고" />);
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
  });

  it("사유를 골라 접수하면 완료 문구를 보여준다", async () => {
    const user = userEvent.setup();
    reportContent.mockResolvedValue(undefined);

    render(<ReportDialog targetType="COMMENT" targetId={5} label="신고" />);
    await user.click(screen.getByRole("button", { name: "신고" }));
    await user.click(screen.getByLabelText("욕설/괴롭힘"));
    await user.click(screen.getByRole("button", { name: "신고하기" }));

    await waitFor(() => {
      expect(reportContent).toHaveBeenCalledWith("COMMENT", 5, "HARASSMENT");
    });
    expect(await screen.findByText("신고가 접수됐습니다. 운영진이 확인합니다.")).toBeInTheDocument();
  });

  it("접수 실패 시 오류 문구를 보여주고 다이얼로그를 유지한다", async () => {
    const user = userEvent.setup();
    reportContent.mockRejectedValue(new Error("network"));

    render(<ReportDialog targetType="POST" targetId={1} label="신고" />);
    await user.click(screen.getByRole("button", { name: "신고" }));
    await user.click(screen.getByRole("button", { name: "신고하기" }));

    expect(
      await screen.findByText("신고를 접수하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByRole("radiogroup")).toBeInTheDocument();
  });
});
