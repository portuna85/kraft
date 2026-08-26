import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

  it("설명 코드가 없으면 설명 칩·자세히 토글을 만들지 않는다", () => {
    render(<RecommendationResultRow index={1} numbers={[1, 2, 3, 4, 5, 6]} />);
    expect(screen.queryByRole("button", { name: "자세히" })).toBeNull();
  });

  // kraft-redesign-plan.md P0: 반복되는 전체 문장 대신 짧은 칩을 기본으로 보여주고,
  // 전체 문장은 "자세히"로 펼쳐야 나타난다.
  it("설명 코드를 짧은 칩으로 기본 노출하고, 자세히를 눌러야 전체 문구가 보인다", async () => {
    const user = userEvent.setup();
    render(
      <RecommendationResultRow
        index={1}
        numbers={[1, 2, 3, 4, 5, 6]}
        explanationCodes={["ODD_EVEN_BALANCED"]}
      />,
    );

    expect(screen.getByText("홀짝 균형")).toBeVisible();
    const fullSentence = screen.getByText("홀짝이 고르게 섞였습니다");
    expect(fullSentence.closest("[hidden]")).not.toBeNull();

    await user.click(screen.getByRole("button", { name: "자세히" }));

    expect(fullSentence.closest("[hidden]")).toBeNull();
    expect(screen.getByRole("button", { name: "간단히" })).toBeInTheDocument();
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
