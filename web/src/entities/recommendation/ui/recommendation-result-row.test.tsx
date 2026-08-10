import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { RecommendationResultRow } from "./recommendation-result-row";

describe("추천 결과 한 줄", () => {
  it("1부터 시작하는 라벨과 번호 6개를 보여준다", () => {
    render(<RecommendationResultRow index={1} numbers={[1, 8, 17, 24, 33, 41]} />);

    expect(screen.getByText("추천 1")).toBeInTheDocument();
    for (const value of [1, 8, 17, 24, 33, 41]) {
      expect(screen.getByText(String(value))).toBeInTheDocument();
    }
  });

  it("설명 코드가 없으면 설명 목록을 만들지 않는다", () => {
    const { container } = render(
      <RecommendationResultRow index={1} numbers={[1, 2, 3, 4, 5, 6]} />,
    );
    expect(container.querySelector("ul")).toBeNull();
  });

  it("설명 코드를 한국어 문구로 보여준다", () => {
    render(
      <RecommendationResultRow
        index={1}
        numbers={[1, 2, 3, 4, 5, 6]}
        explanationCodes={["ODD_EVEN_BALANCED"]}
      />,
    );
    expect(screen.getByText("홀짝이 고르게 섞였습니다")).toBeInTheDocument();
  });

  it("action이 없으면 렌더하지 않는다", () => {
    const { container } = render(
      <RecommendationResultRow index={1} numbers={[1, 2, 3, 4, 5, 6]} />,
    );
    expect(container.querySelector("button")).toBeNull();
  });

  it("오른쪽 action 슬롯을 렌더한다", () => {
    render(
      <RecommendationResultRow
        index={2}
        numbers={[1, 2, 3, 4, 5, 6]}
        action={<button type="button">보관함에 저장</button>}
      />,
    );
    expect(screen.getByRole("button", { name: "보관함에 저장" })).toBeInTheDocument();
  });
});
