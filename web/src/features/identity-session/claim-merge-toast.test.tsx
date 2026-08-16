import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { SessionContext, type SessionState } from "@/entities/user-session/session-context";
import { ToastProvider } from "@/shared/ui/toast";

import { ClaimMergeToast } from "./claim-merge-toast";

function session(claimStatus: SessionState["claimStatus"]): SessionState {
  return {
    session: { loggedIn: true, userId: 1, nickname: "나", activeProviders: ["google"] },
    loading: false,
    error: false,
    claimStatus,
    retry: vi.fn(),
  };
}

function renderWithStatus(claimStatus: SessionState["claimStatus"]) {
  return render(
    <ToastProvider>
      <SessionContext.Provider value={session(claimStatus)}>
        <ClaimMergeToast />
      </SessionContext.Provider>
    </ToastProvider>,
  );
}

describe("ClaimMergeToast", () => {
  // UX-04: claimStatus 상태머신은 이미 있었지만 아무도 구독하지 않아 익명→계정
  // 병합이 일어나도 사용자에게 아무 신호가 없었다.
  it("claiming에서 settled로 바뀌면 병합 안내를 띄운다", () => {
    const { rerender } = renderWithStatus("claiming");
    expect(screen.queryByText(/계정으로 옮겼습니다/)).not.toBeInTheDocument();

    rerender(
      <ToastProvider>
        <SessionContext.Provider value={session("settled")}>
          <ClaimMergeToast />
        </SessionContext.Provider>
      </ToastProvider>,
    );

    expect(screen.getByText(/계정으로 옮겼습니다/)).toBeInTheDocument();
  });

  // 옮길 기록이 없던 로그인은 claiming을 거치지 않고 바로 settled다 — 매 로그인마다
  // 잘못된 토스트가 뜨면 안 된다.
  it("claiming을 거치지 않고 바로 settled면 안내하지 않는다", () => {
    renderWithStatus("settled");
    expect(screen.queryByText(/계정으로 옮겼습니다/)).not.toBeInTheDocument();
  });
});
