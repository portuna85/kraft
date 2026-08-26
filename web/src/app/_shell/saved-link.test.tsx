import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SavedLink } from "./saved-link";

let mockPathname = "/";

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
}));

describe("SavedLink", () => {
  beforeEach(() => {
    mockPathname = "/";
  });

  it("링크는 /saved를 가리킨다", () => {
    render(<SavedLink />);
    expect(screen.getByRole("link", { name: "보관함" })).toHaveAttribute("href", "/saved");
  });

  it("/saved에서 aria-current=page를 갖는다", () => {
    mockPathname = "/saved";
    render(<SavedLink />);
    expect(screen.getByRole("link", { name: "보관함" })).toHaveAttribute("aria-current", "page");
  });

  it("다른 경로에서는 aria-current를 갖지 않는다", () => {
    mockPathname = "/recommend";
    render(<SavedLink />);
    expect(screen.getByRole("link", { name: "보관함" })).not.toHaveAttribute("aria-current");
  });
});
