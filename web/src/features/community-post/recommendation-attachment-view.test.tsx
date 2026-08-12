import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { RecommendationAttachment } from "@/entities/community-post/schema";

import { RecommendationAttachmentView } from "./recommendation-attachment-view";

const ATTACHMENT: RecommendationAttachment = {
  setId: 1,
  strategy: "balanced",
  algorithmVersion: "v1",
  historyThroughRound: 1150,
  exclusionPolicyVersion: "v2",
  items: [
    {
      position: 1,
      numbers: [1, 2, 3, 4, 5, 6],
      score: null,
      explanationCodes: ["ODD_EVEN_BALANCED"],
    },
    {
      position: 2,
      numbers: [7, 8, 9, 10, 11, 12],
      score: null,
      explanationCodes: [],
    },
  ],
};

describe("추천 첨부 뷰", () => {
  it("전략·알고리즘 버전·반영 회차·제외 정책을 모두 보여준다", () => {
    render(<RecommendationAttachmentView attachment={ATTACHMENT} />);

    expect(screen.getByText(/균형 잡힌 조합/)).toBeInTheDocument();
    expect(screen.getByText(/1150회까지 반영/)).toBeInTheDocument();
    expect(screen.getByText(/알고리즘 v1/)).toBeInTheDocument();
    expect(screen.getByText(/제외 정책 v2/)).toBeInTheDocument();
  });

  it("항목마다 한 행씩 렌더한다", () => {
    render(<RecommendationAttachmentView attachment={ATTACHMENT} />);

    expect(screen.getByText("추천 1")).toBeInTheDocument();
    expect(screen.getByText("추천 2")).toBeInTheDocument();
  });
});
