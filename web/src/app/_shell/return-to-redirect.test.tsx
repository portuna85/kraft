import { render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ReturnToRedirect } from "./return-to-redirect";

const replace = vi.fn();
let mockPathname = "/";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => mockPathname,
}));

describe("ReturnToRedirect", () => {
  beforeEach(() => {
    replace.mockClear();
    window.sessionStorage.clear();
    mockPathname = "/";
  });

  it("저장된 복귀 경로가 있으면 그 경로로 이동한다", () => {
    window.sessionStorage.setItem("kraft-return-to", "/community/write");

    render(<ReturnToRedirect />);

    expect(replace).toHaveBeenCalledWith("/community/write");
  });

  it("저장된 값이 없으면 아무 것도 하지 않는다", () => {
    render(<ReturnToRedirect />);

    expect(replace).not.toHaveBeenCalled();
  });

  it("저장된 경로가 현재 경로와 같으면 이동하지 않는다", () => {
    mockPathname = "/community/write";
    window.sessionStorage.setItem("kraft-return-to", "/community/write");

    render(<ReturnToRedirect />);

    expect(replace).not.toHaveBeenCalled();
  });

  it("저장된 값을 한 번 소비한 뒤에는 다시 사용하지 않는다", () => {
    window.sessionStorage.setItem("kraft-return-to", "/community/write");

    render(<ReturnToRedirect />);

    expect(window.sessionStorage.getItem("kraft-return-to")).toBeNull();
  });
});
