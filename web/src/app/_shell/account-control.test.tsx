import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { SessionContext, type SessionState } from "@/entities/user-session/session-context";

import { AccountControl } from "./account-control";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), back: vi.fn() }),
  usePathname: () => "/",
  useSearchParams: () => new URLSearchParams(),
}));

const LOADING: SessionState = {
  session: null,
  loading: true,
  error: false,
  claimStatus: "idle",
  retry: vi.fn(),
};

const ANONYMOUS: SessionState = {
  session: { loggedIn: false, userId: null, nickname: null, activeProviders: ["google", "naver"] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

const LOGGED_IN: SessionState = {
  session: { loggedIn: true, userId: 10, nickname: "당첨기원", activeProviders: ["google"] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

function renderWith(session: SessionState) {
  return render(
    <SessionContext.Provider value={session}>
      <AccountControl />
    </SessionContext.Provider>,
  );
}

describe("계정 영역 분기", () => {
  it("세션 판정 전에는 아무것도 그리지 않는다", () => {
    renderWith(LOADING);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("비로그인은 로그인 팝오버를 보여준다", () => {
    renderWith(ANONYMOUS);
    expect(screen.getByRole("button", { name: "로그인" })).toBeInTheDocument();
  });

  it("로그인 상태는 계정 메뉴를 보여준다", () => {
    renderWith(LOGGED_IN);
    expect(screen.getByRole("button", { name: "당첨기원님" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "로그인" })).not.toBeInTheDocument();
  });
});
