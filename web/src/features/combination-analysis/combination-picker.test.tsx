import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { CombinationPicker } from "./combination-picker";

function input() {
  return screen.getByLabelText("번호 직접 입력") as HTMLInputElement;
}

describe("조합 입력", () => {
  it("번호판에서 고른 값이 직접 입력 칸에 그대로 나타난다", async () => {
    const user = userEvent.setup();
    render(<CombinationPicker initialNumbers={[]} />);

    await user.click(screen.getByRole("button", { name: "7번" }));
    await user.click(screen.getByRole("button", { name: "19번" }));

    expect(input().value).toBe("7, 19");
  });

  it("직접 입력한 값이 번호판 선택으로 반영된다", async () => {
    const user = userEvent.setup();
    render(<CombinationPicker initialNumbers={[]} />);

    await user.type(input(), "3, 14");

    // 양방향 동기화가 없으면 번호판은 아무것도 고르지 않은 채로 남는다.
    expect(screen.getByRole("button", { name: "3번 고정됨" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "14번 고정됨" })).toBeInTheDocument();
  });

  it("같은 번호를 다시 누르면 선택이 풀린다", async () => {
    const user = userEvent.setup();
    render(<CombinationPicker initialNumbers={[]} />);

    await user.click(screen.getByRole("button", { name: "7번" }));
    await user.click(screen.getByRole("button", { name: "7번 고정됨" }));

    expect(input().value).toBe("");
  });

  it("6개를 채우기 전에는 분석 버튼을 누를 수 없다", async () => {
    const user = userEvent.setup();
    render(<CombinationPicker initialNumbers={[1, 2, 3]} />);

    expect(screen.getByRole("button", { name: "분석하기" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "4번" }));
    await user.click(screen.getByRole("button", { name: "5번" }));
    await user.click(screen.getByRole("button", { name: "6번" }));

    expect(screen.getByRole("button", { name: "분석하기" })).toBeEnabled();
  });

  it("남은 개수를 소리로도 알린다", async () => {
    const user = userEvent.setup();
    render(<CombinationPicker initialNumbers={[]} />);

    // 시각적으로만 알리면 화면을 못 보는 사용자는 몇 개를 골랐는지 알 수 없다.
    expect(screen.getByText("6개를 골라 주세요.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "7번" }));
    expect(screen.getByText("6개 중 1개 선택됨 — 5개 더 필요합니다.")).toBeInTheDocument();
  });

  it("직접 입력에 같은 번호를 중복으로 치면 이유를 알리고 분석 버튼을 막는다", async () => {
    const user = userEvent.setup();
    render(<CombinationPicker initialNumbers={[]} />);

    await user.type(input(), "1, 1, 1, 1, 1, 7");

    // 개수만 보면 6개를 다 채운 것처럼 보이지만, 중복이라 완전한 조합이 아니다 —
    // 그 이유가 화면에 텍스트로 남아 있어야 한다(조용히 막히면 왜 막혔는지 알 수 없다).
    expect(
      screen.getByText("같은 번호를 두 번 이상 입력했습니다. 서로 다른 번호를 입력하세요."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "분석하기" })).toBeDisabled();
    // 남은 개수 문구도 고유 개수(1과 7, 2개) 기준이라 "0개 더 필요"라는 모순된 안내를 하지 않는다.
    expect(screen.getByText("6개 중 2개 선택됨 — 4개 더 필요합니다.")).toBeInTheDocument();
  });

  it("7번째를 고르면 가장 먼저 고른 번호를 밀어낸다", async () => {
    const user = userEvent.setup();
    render(<CombinationPicker initialNumbers={[1, 2, 3, 4, 5, 6]} />);

    await user.click(screen.getByRole("button", { name: "40번" }));

    // 조용히 무시하면 눌렀는데 아무 일도 안 일어난 것처럼 보인다.
    expect(input().value).toBe("2, 3, 4, 5, 6, 40");
  });

  it("초기화하면 선택이 모두 풀린다", async () => {
    const user = userEvent.setup();
    render(<CombinationPicker initialNumbers={[1, 2, 3]} />);

    await user.click(screen.getByRole("button", { name: "초기화" }));

    expect(input().value).toBe("");
    expect(screen.getByRole("button", { name: "초기화" })).toBeDisabled();
  });

  it("URL로 받은 번호를 초기 선택으로 복원한다", () => {
    // 분석 결과 링크를 열었을 때 입력 칸이 비어 있으면 조합을 다시 쳐야 한다.
    render(<CombinationPicker initialNumbers={[1, 8, 17, 24, 33, 41]} />);
    expect(input().value).toBe("1, 8, 17, 24, 33, 41");
  });

  it("GET 폼이라 JS 없이도 직접 입력으로 분석할 수 있다", () => {
    render(<CombinationPicker initialNumbers={[]} />);

    const form = input().closest("form");
    expect(form).toHaveAttribute("method", "get");
    expect(form).toHaveAttribute("action", "/analysis");
    expect(input()).toHaveAttribute("name", "numbers");
  });
});
