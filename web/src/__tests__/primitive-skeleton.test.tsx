import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { Skeleton } from "@/ui/primitives/skeleton";

describe("Skeleton 프리미티브", () => {
  it("장식용이므로 스크린리더에서 숨긴다", () => {
    const { container } = render(<Skeleton />);
    expect(container.firstElementChild).toHaveAttribute("aria-hidden", "true");
  });

  it("variant=text는 lines 개수만큼 줄을 렌더링한다", () => {
    const { container } = render(<Skeleton variant="text" lines={3} />);
    expect(container.firstElementChild?.children).toHaveLength(3);
  });

  it("variant=card/ball은 단일 요소를 렌더링한다", () => {
    const { container: cardContainer } = render(<Skeleton variant="card" />);
    expect(cardContainer.firstElementChild?.children).toHaveLength(0);

    const { container: ballContainer } = render(<Skeleton variant="ball" />);
    expect(ballContainer.firstElementChild?.children).toHaveLength(0);
  });
});
