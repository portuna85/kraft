import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PatternDistribution } from "./pattern-distribution";

const BUCKETS = [
  { bucketKey: "0", count: 10 },
  { bucketKey: "1", count: 60 },
  { bucketKey: "2", count: 30 },
];

const format = (bucketKey: string) => `${bucketKey}개`;

describe("구간 분포", () => {
  it("막대가 아니라 셀 텍스트로도 값을 읽을 수 있다", () => {
    // 막대만 있고 숫자가 없으면 스크린리더에는 빈 표가 된다. 이 표의 값은 항상
    // 텍스트로 존재해야 한다.
    render(<PatternDistribution caption="홀수 개수" buckets={BUCKETS} formatBucketKey={format} />);

    expect(screen.getByText("10회")).toBeInTheDocument();
    expect(screen.getByText("60회")).toBeInTheDocument();
    expect(screen.getByText("30회")).toBeInTheDocument();
  });

  it("표에 이름을 준다", () => {
    render(<PatternDistribution caption="홀수 개수" buckets={BUCKETS} formatBucketKey={format} />);
    expect(screen.getByRole("table", { name: "홀수 개수" })).toBeInTheDocument();
  });

  it("구간을 행 머리글로 둔다", () => {
    render(<PatternDistribution caption="홀수 개수" buckets={BUCKETS} formatBucketKey={format} />);

    const rowHeaders = screen.getAllByRole("rowheader");
    expect(rowHeaders.map((cell) => cell.textContent)).toEqual(["0개", "1개", "2개"]);
  });

  it("비율을 버킷 합 기준으로 계산한다", () => {
    render(<PatternDistribution caption="홀수 개수" buckets={BUCKETS} formatBucketKey={format} />);

    // 60 / 100 = 60.0%. 최대값(60)이 아니라 합(100)이 분모여야 한다.
    const row = screen.getByRole("rowheader", { name: "1개" }).closest("tr");
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getByText("60.0%")).toBeInTheDocument();
  });

  it("빈 버킷 묶음에서 0으로 나누지 않는다", () => {
    render(<PatternDistribution caption="홀수 개수" buckets={[]} formatBucketKey={format} />);
    expect(screen.getByRole("table", { name: "홀수 개수" })).toBeInTheDocument();
  });
});
