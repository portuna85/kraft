import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { RecommendationItem } from "../schema";
import { RecommendationCard } from "./recommendation-card";

const ITEMS: RecommendationItem[] = [
  {
    position: 0,
    numbers: [1, 8, 17, 24, 33, 41],
    score: null,
    explanationCodes: ["ODD_EVEN_BALANCED"],
  },
  { position: 1, numbers: [2, 9, 18, 25, 34, 42], score: null, explanationCodes: [] },
];

describe("추천 카드", () => {
  it("전략·기준 회차·생성 시각을 보여준다", () => {
    render(
      <RecommendationCard
        strategy="balanced"
        createdAt="2026-08-01T12:00:00Z"
        historyThroughRound={1150}
        items={ITEMS}
      />,
    );

    expect(screen.getByText(/균형 잡힌 조합/)).toBeInTheDocument();
    expect(screen.getByText(/1150회까지 반영/)).toBeInTheDocument();
  });

  it("설명 코드를 한국어 문구로 보여준다", () => {
    render(
      <RecommendationCard
        strategy="balanced"
        createdAt="2026-08-01T12:00:00Z"
        historyThroughRound={1150}
        items={ITEMS}
      />,
    );

    expect(screen.getByText("홀짝이 고르게 섞였습니다")).toBeInTheDocument();
  });

  it("설명 코드가 없는 항목에는 설명 칩·자세히 토글을 만들지 않는다", () => {
    render(
      <RecommendationCard
        strategy="balanced"
        createdAt="2026-08-01T12:00:00Z"
        historyThroughRound={1150}
        items={[ITEMS[1] as RecommendationItem]}
      />,
    );

    expect(screen.queryByRole("button", { name: "자세히" })).toBeNull();
  });

  it("저장 슬롯이 없으면 저장 버튼을 렌더하지 않는다", () => {
    render(
      <RecommendationCard
        strategy="balanced"
        createdAt="2026-08-01T12:00:00Z"
        historyThroughRound={1150}
        items={ITEMS}
      />,
    );
    expect(screen.queryByRole("button", { name: /^저장/ })).toBeNull();
  });

  it("저장 슬롯을 항목별로 렌더한다", () => {
    render(
      <RecommendationCard
        strategy="balanced"
        createdAt="2026-08-01T12:00:00Z"
        historyThroughRound={1150}
        items={ITEMS}
        renderSaveSlot={(item) => <button type="button">저장 {item.position}</button>}
      />,
    );

    expect(screen.getByRole("button", { name: "저장 0" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "저장 1" })).toBeInTheDocument();
  });
});
