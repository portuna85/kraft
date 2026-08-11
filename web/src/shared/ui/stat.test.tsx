import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Stat } from "./stat";

describe("Stat", () => {
  it("label과 value를 함께 보여준다", () => {
    render(<Stat label="최신 회차" value="1150회" />);
    expect(screen.getByText("최신 회차")).toBeInTheDocument();
    expect(screen.getByText("1150회")).toBeInTheDocument();
  });

  it("ReactNode value도 받는다", () => {
    render(<Stat label="상태" value={<strong>정상</strong>} />);
    expect(screen.getByText("정상").tagName).toBe("STRONG");
  });
});
