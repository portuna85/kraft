import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { THEME_STORAGE_KEY } from "@/shared/lib/theme";

import { ThemeToggle } from "./theme-toggle";

describe("ThemeToggle", () => {
  beforeEach(() => {
    document.documentElement.dataset.theme = "light";
    window.localStorage.removeItem(THEME_STORAGE_KEY);
  });

  afterEach(() => {
    delete document.documentElement.dataset.theme;
  });

  // I-33: 다크 모드는 OS 설정으로만 도달할 수 있었다 — 이 버튼이 유일한 수동 진입점이다.
  it("현재 테마를 반영한 라벨을 보여주고 누르면 반대 테마로 바꾼다", async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);

    const button = await screen.findByRole("button", { name: "다크 모드로 전환" });
    await user.click(button);

    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");
    expect(await screen.findByRole("button", { name: "라이트 모드로 전환" })).toBeInTheDocument();
  });
});
