import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/shared/api/error";
import { DEVICE_TOKEN_STORAGE_KEY } from "@/shared/api/device-token";
import { resetResourceCacheForTests } from "@/shared/hooks/use-resource";

import { clearClaimFlagForTests } from "./claim-flag";
import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";

import { SessionProvider } from "./session-provider";

const fetchSession = vi.fn();
const claimDevice = vi.fn();

vi.mock("@/entities/user-session/api", () => ({
  SESSION_RESOURCE_KEY: "session",
  fetchSession: (signal: AbortSignal) => fetchSession(signal),
  claimDevice: () => claimDevice(),
}));

function Probe() {
  const state = useSession();
  return (
    <div>
      <p>loading: {String(state.loading)}</p>
      <p>error: {String(state.error)}</p>
      <p>session: {state.session === null ? "모름" : String(state.session.loggedIn)}</p>
      <p>claim: {state.claimStatus}</p>
      <p>ownerScope: {String(canQueryOwnerScope(state))}</p>
      <button type="button" onClick={state.retry}>
        다시 시도
      </button>
    </div>
  );
}

function renderProvider() {
  return render(
    <SessionProvider>
      <Probe />
    </SessionProvider>,
  );
}

const LOGGED_IN = {
  loggedIn: true,
  userId: 7,
  nickname: "당첨기원",
  activeProviders: ["google"],
};

beforeEach(() => {
  window.localStorage.clear();
  clearClaimFlagForTests();
  fetchSession.mockReset();
  claimDevice.mockReset();
});

afterEach(() => {
  resetResourceCacheForTests();
});

describe("세션 상태머신", () => {
  it("조회 실패를 비로그인으로 축약하지 않고 error로 구분한다 (I-3)", async () => {
    fetchSession.mockRejectedValue(new ApiError("network", "연결 실패"));
    renderProvider();

    await waitFor(() => expect(screen.getByText("error: true")).toBeInTheDocument());
    // 여기서 session이 "false"(비로그인)로 보이면 로그인 링크가 사라져 복구 경로가 없어진다.
    expect(screen.getByText("session: 모름")).toBeInTheDocument();
  });

  it("조회 실패 시 재시도 경로를 제공한다 (FE-005)", async () => {
    fetchSession
      .mockRejectedValueOnce(new ApiError("network", "연결 실패"))
      .mockResolvedValue({ loggedIn: false, userId: null, nickname: null, activeProviders: [] });
    renderProvider();

    await screen.findByText("error: true");
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    await waitFor(() => expect(screen.getByText("session: false")).toBeInTheDocument());
  });

  it("비로그인 세션에서는 claim을 시도하지 않는다", async () => {
    fetchSession.mockResolvedValue({
      loggedIn: false,
      userId: null,
      nickname: null,
      activeProviders: ["google", "naver"],
    });
    renderProvider();

    await screen.findByText("session: false");
    expect(claimDevice).not.toHaveBeenCalled();
  });

  it("익명 토큰이 없으면 옮길 기록이 없으므로 claim을 건너뛴다", async () => {
    fetchSession.mockResolvedValue(LOGGED_IN);
    renderProvider();

    await waitFor(() => expect(screen.getByText("claim: settled")).toBeInTheDocument());
    expect(claimDevice).not.toHaveBeenCalled();
  });

  it("claim 성공 시 토큰을 회전한다 (I-2)", async () => {
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "old-token");
    fetchSession.mockResolvedValue(LOGGED_IN);
    claimDevice.mockResolvedValue(null);

    renderProvider();

    await waitFor(() => expect(screen.getByText("claim: settled")).toBeInTheDocument());
    const rotated = window.localStorage.getItem(DEVICE_TOKEN_STORAGE_KEY);
    expect(rotated).not.toBeNull();
    expect(rotated).not.toBe("old-token");
  });

  it("claim 실패 시 토큰을 회전하지 않는다 (I-2 / F-P0-10, T-5)", async () => {
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "old-token");
    fetchSession.mockResolvedValue(LOGGED_IN);
    claimDevice.mockRejectedValue(new ApiError("server", "실패"));

    renderProvider();

    await waitFor(() => expect(screen.getByText("claim: error")).toBeInTheDocument());
    // 회전했다면 이 브라우저의 익명 저장 기록은 영구히 접근 불가가 된다.
    expect(window.localStorage.getItem(DEVICE_TOKEN_STORAGE_KEY)).toBe("old-token");
  });

  it("claim 실패를 사용자 오류로 노출하지 않는다", async () => {
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "old-token");
    fetchSession.mockResolvedValue(LOGGED_IN);
    claimDevice.mockRejectedValue(new ApiError("client", "이미 귀속됨", { status: 409 }));

    renderProvider();

    await screen.findByText("claim: error");
    // 세션 조회는 성공했으므로 error는 false여야 한다 — claim 실패가 세션 오류로 번지면
    // 로그인에 성공한 사용자에게 실패 화면이 뜬다.
    expect(screen.getByText("error: false")).toBeInTheDocument();
  });

  it("claim 실패여도 소유자 스코프 조회는 계속 진행한다 (I-1)", async () => {
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "old-token");
    fetchSession.mockResolvedValue(LOGGED_IN);
    claimDevice.mockRejectedValue(new ApiError("server", "실패"));

    renderProvider();

    await screen.findByText("claim: error");
    expect(screen.getByText("ownerScope: true")).toBeInTheDocument();
  });

  it("claim이 끝난 탭에서는 다시 시도하지 않는다", async () => {
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "old-token");
    fetchSession.mockResolvedValue(LOGGED_IN);
    claimDevice.mockResolvedValue(null);

    const first = renderProvider();
    await waitFor(() => expect(claimDevice).toHaveBeenCalledTimes(1));
    first.unmount();
    resetResourceCacheForTests();

    renderProvider();
    await waitFor(() => expect(screen.getByText("claim: settled")).toBeInTheDocument());
    expect(claimDevice).toHaveBeenCalledTimes(1);
  });

  it("claim 실패는 플래그를 남기지 않아 다음 마운트에서 다시 시도한다", async () => {
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "old-token");
    fetchSession.mockResolvedValue(LOGGED_IN);
    claimDevice.mockRejectedValue(new ApiError("network", "실패"));

    const first = renderProvider();
    await waitFor(() => expect(claimDevice).toHaveBeenCalledTimes(1));
    first.unmount();
    resetResourceCacheForTests();

    renderProvider();
    await waitFor(() => expect(claimDevice).toHaveBeenCalledTimes(2));
  });
});

describe("useSession", () => {
  it("세션 셸 밖에서 쓰면 조용한 기본값 대신 오류를 낸다 (I-4)", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<Probe />)).toThrow(/session/);
    consoleError.mockRestore();
  });
});
