import { render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ReturnToRedirect } from "./return-to-redirect";

const replace = vi.fn();
const refresh = vi.fn();
let mockPathname = "/";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, refresh }),
  usePathname: () => mockPathname,
}));

describe("ReturnToRedirect", () => {
  beforeEach(() => {
    replace.mockClear();
    refresh.mockClear();
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

  // I-03: 이동은 필요 없지만, 로그인 직후 서버 컴포넌트가 새 쿠키(kraft_logged_in)를
  // 반영해 다시 렌더링되도록 router.refresh()는 호출해야 한다.
  it("저장된 경로가 현재 경로와 같으면 이동 대신 새로고침한다", () => {
    mockPathname = "/community/write";
    window.sessionStorage.setItem("kraft-return-to", "/community/write");

    render(<ReturnToRedirect />);

    expect(replace).not.toHaveBeenCalled();
    expect(refresh).toHaveBeenCalledOnce();
  });

  it("저장된 값을 한 번 소비한 뒤에는 다시 사용하지 않는다", () => {
    window.sessionStorage.setItem("kraft-return-to", "/community/write");

    render(<ReturnToRedirect />);

    expect(window.sessionStorage.getItem("kraft-return-to")).toBeNull();
  });
});
