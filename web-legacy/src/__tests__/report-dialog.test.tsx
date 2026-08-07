import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ReportDialog } from "@/features/community/report-dialog";

const reportContent = vi.fn();

vi.mock("@/lib/community-client", () => ({
  reportContent: (...args: unknown[]) => reportContent(...args),
}));

describe("신고 다이얼로그", () => {
  beforeEach(() => {
    reportContent.mockReset();
  });

  it("F-P0-8: 신고 성공 시 다이얼로그를 닫지 않고 완료 메시지를 보여준다", async () => {
    reportContent.mockResolvedValue(undefined);

    render(<ReportDialog targetType="POST" targetId={1} />);
    fireEvent.click(screen.getByRole("button", { name: "신고" }));
    fireEvent.click(await screen.findByRole("button", { name: "신고 제출" }));

    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent("신고가 접수되었습니다.");
    });
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("F-P0-8: 완료 메시지 확인 후 확인 버튼을 누르면 다이얼로그가 닫힌다", async () => {
    reportContent.mockResolvedValue(undefined);

    render(<ReportDialog targetType="POST" targetId={1} />);
    fireEvent.click(screen.getByRole("button", { name: "신고" }));
    fireEvent.click(await screen.findByRole("button", { name: "신고 제출" }));
    fireEvent.click(await screen.findByRole("button", { name: "확인" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  it("신고 실패 시 다이얼로그를 열어둔 채 오류 메시지를 보여준다", async () => {
    reportContent.mockRejectedValue(new Error("REPORT_ALREADY_EXISTS"));

    render(<ReportDialog targetType="POST" targetId={1} />);
    fireEvent.click(screen.getByRole("button", { name: "신고" }));
    fireEvent.click(await screen.findByRole("button", { name: "신고 제출" }));

    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent("이미 신고했거나 처리하지 못했습니다.");
    });
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
