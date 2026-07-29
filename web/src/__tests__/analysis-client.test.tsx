import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AnalysisClient } from "@/components/analysis-client";

const ANALYSIS_RESPONSE = {
  numbers: [3, 11, 19, 28, 34, 42],
  oddCount: 3,
  evenCount: 3,
  lowCount: 3,
  highCount: 3,
  sumOfNumbers: 137,
  sumBucket: "111-155",
  consecutivePairCount: 0,
  rangeDistribution: [
    { range: "1-9", count: 1 },
    { range: "10-19", count: 2 },
    { range: "20-29", count: 1 },
    { range: "30-39", count: 1 },
    { range: "40-45", count: 1 },
  ],
  wonFirstPrize: false,
  firstPrizeHistory: [],
};

describe("번호 분석 입력 검증 (F4: 공통 validator 재사용)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(ANALYSIS_RESPONSE),
    });
  });

  it("정수가 아닌 값(예: 3x)이 섞이면 오류를 보여준다", () => {
    render(<AnalysisClient />);

    fireEvent.change(screen.getByRole("textbox"), {
      target: { value: "3x, 11, 19, 28, 34, 42" },
    });
    fireEvent.click(screen.getByRole("button", { name: "분석하기" }));

    expect(screen.getByRole("alert")).toHaveTextContent("정수여야 합니다");
  });

  it("6개 미만이면 오류를 보여준다", () => {
    render(<AnalysisClient />);

    fireEvent.change(screen.getByRole("textbox"), { target: { value: "3, 11, 19" } });
    fireEvent.click(screen.getByRole("button", { name: "분석하기" }));

    expect(screen.getByRole("alert")).toHaveTextContent("6개여야 합니다");
  });

  it("유효한 번호 6개를 서버에서 분석하고 당첨 이력 점검 결과를 보여준다", async () => {
    render(<AnalysisClient />);

    fireEvent.change(screen.getByRole("textbox"), {
      target: { value: "3, 11, 19, 28, 34, 42" },
    });
    fireEvent.click(screen.getByRole("button", { name: "분석하기" }));

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "분석 결과" })).toBeInTheDocument();
    });
    expect(screen.getByText("역대 1등 당첨 이력 없음")).toBeInTheDocument();
    expect(global.fetch).toHaveBeenCalledWith(
      "/api/v1/stats/analysis",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ numbers: [3, 11, 19, 28, 34, 42] }),
      }),
    );
  });

  it("서버 점검 실패 시 결과를 오표기하지 않고 오류를 보여준다", async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error("network down"));
    render(<AnalysisClient />);

    fireEvent.change(screen.getByRole("textbox"), {
      target: { value: "3, 11, 19, 28, 34, 42" },
    });
    fireEvent.click(screen.getByRole("button", { name: "분석하기" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("불러오지 못했습니다");
    });
    expect(screen.queryByRole("heading", { name: "분석 결과" })).not.toBeInTheDocument();
  });
});
