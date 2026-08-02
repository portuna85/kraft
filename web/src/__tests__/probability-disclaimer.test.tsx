import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  HISTORICAL_EXCLUSION_TEXT,
  HISTORY_DISCLAIMER_TEXT,
  PROBABILITY_DISCLAIMER_TEXT,
  ProbabilityDisclaimer,
} from "@/features/recommendation/disclaimer";

describe("ProbabilityDisclaimer", () => {
  it("세 안내 문구를 일반 문단으로 제공하고 비권장 note 역할을 사용하지 않는다", () => {
    const { container } = render(<ProbabilityDisclaimer />);

    expect(screen.getByText(PROBABILITY_DISCLAIMER_TEXT).tagName).toBe("P");
    expect(screen.getByText(HISTORY_DISCLAIMER_TEXT).tagName).toBe("P");
    expect(screen.getByText(HISTORICAL_EXCLUSION_TEXT).tagName).toBe("P");
    expect(container.querySelectorAll('[role="note"]')).toHaveLength(0);
  });
});
