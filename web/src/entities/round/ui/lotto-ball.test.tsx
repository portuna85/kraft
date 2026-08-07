import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { LottoBallSet } from "./lotto-ball";

describe("당첨 번호 표시", () => {
  it("번호 6개가 실제로 화면에 보인다 (T-1의 컴포넌트 단위 대응)", () => {
    render(<LottoBallSet numbers={[3, 11, 24, 30, 38, 44]} />);

    for (const value of [3, 11, 24, 30, 38, 44]) {
      expect(screen.getByText(String(value))).toBeInTheDocument();
    }
  });

  it("번호를 순서 있는 목록으로 전달한다", () => {
    render(<LottoBallSet numbers={[3, 11, 24, 30, 38, 44]} />);
    expect(screen.getAllByRole("listitem")).toHaveLength(6);
  });

  it("보너스 번호를 기호가 아니라 말로 구분한다", () => {
    render(<LottoBallSet numbers={[3, 11, 24, 30, 38, 44]} bonusNumber={7} />);

    // "+"만 있으면 스크린리더 사용자는 무엇이 보너스인지 알 수 없다.
    expect(screen.getByText("보너스 번호")).toBeInTheDocument();
    expect(screen.getByText("7")).toBeInTheDocument();
  });

  it("보너스가 없으면 보너스 표기를 만들지 않는다", () => {
    render(<LottoBallSet numbers={[3, 11, 24, 30, 38, 44]} />);
    expect(screen.queryByText("보너스 번호")).not.toBeInTheDocument();
  });
});
