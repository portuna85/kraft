import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { AnalysisResult } from "@/app/analysis/analysis-result";
import type { AnalysisResponse } from "@/lib/api";

const analysis: AnalysisResponse = {
  numbers: [1, 2, 3, 4, 5, 6],
  oddCount: 3,
  evenCount: 3,
  lowCount: 6,
  highCount: 0,
  sumOfNumbers: 21,
  sumBucket: "21-65",
  consecutivePairCount: 5,
  wonFirstPrize: true,
  firstPrizeHistory: [
    { round: 1, drawDate: "2002-12-07", firstPrizeAmount: 0 },
  ],
  rangeDistribution: [
    { range: "1-9", count: 6 },
    { range: "10-19", count: 0 },
    { range: "20-29", count: 0 },
    { range: "30-39", count: 0 },
    { range: "40-45", count: 0 },
  ],
};

describe("번호 분석 결과", () => {
  it("전달된 title을 제목으로 렌더링한다", () => {
    render(<AnalysisResult analysis={analysis} title="당첨 번호 분석" />);

    expect(screen.getByRole("heading", { name: "당첨 번호 분석" })).toBeInTheDocument();
  });

  it("분석 수치를 화면에 표시한다", () => {
    render(<AnalysisResult analysis={analysis} title="분석 결과" />);

    expect(screen.getByText("3 / 3")).toBeInTheDocument();
    expect(screen.getByText("6 / 0")).toBeInTheDocument();
    expect(screen.getByText("21")).toBeInTheDocument();
    expect(screen.getByText("21-65 구간")).toBeInTheDocument();
    expect(screen.getByText("5쌍")).toBeInTheDocument();
  });

  it("구간 분포를 5개 항목으로 렌더링한다", () => {
    render(<AnalysisResult analysis={analysis} title="분석 결과" />);

    expect(screen.getByRole("heading", { name: "구간 분포", level: 3 })).toBeInTheDocument();
    expect(screen.getAllByText(/^\d+-\d+$/)).toHaveLength(5);
  });

  it("과거 1등 당첨 회차·추첨일·당첨금을 표시한다", () => {
    render(<AnalysisResult analysis={analysis} title="분석 결과" />);

    expect(screen.getByText("역대 1등 당첨 이력 1건")).toBeInTheDocument();
    expect(screen.getByText("1회")).toBeInTheDocument();
    expect(screen.getByText("2002년 12월 7일 토")).toBeInTheDocument();
    expect(screen.getByText("1등 당첨금 0원")).toBeInTheDocument();
  });

  it("당첨 이력이 없음을 명시한다", () => {
    render(
      <AnalysisResult
        analysis={{ ...analysis, wonFirstPrize: false, firstPrizeHistory: [] }}
        title="분석 결과"
      />,
    );

    expect(screen.getByText("역대 1등 당첨 이력 없음")).toBeInTheDocument();
  });
});
