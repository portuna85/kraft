import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";
import {
  CommunitySessionProvider,
  useCommunitySession,
} from "@/components/community/community-session-provider";

const claimDevice = vi.fn();
const rotateDeviceToken = vi.fn();

vi.mock("@/lib/community-client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/community-client")>("@/lib/community-client");
  return {
    ...actual,
    claimDevice: (...args: unknown[]) => claimDevice(...args),
  };
});

vi.mock("@/lib/device-token", () => ({
  rotateDeviceToken: (...args: unknown[]) => rotateDeviceToken(...args),
}));

function Probe() {
  useCommunitySession();
  return null;
}

function mockSessionFetch(loggedIn: boolean) {
  return vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: () => Promise.resolve({ loggedIn, userId: loggedIn ? 1 : null, nickname: loggedIn ? "글쓴이" : null, activeProviders: [] }),
  });
}

describe("커뮤니티 세션 프로바이더의 기기 귀속 트리거", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    claimDevice.mockReset().mockResolvedValue({
      mergedSavedNumberCount: 0,
      duplicateSavedNumberCount: 0,
      mergedRecommendationSetCount: 0,
    });
    rotateDeviceToken.mockReset();
    window.sessionStorage.clear();
  });

  it("로그인 상태가 확인되면 기기 귀속을 시도하고 토큰을 회전한다", async () => {
    global.fetch = mockSessionFetch(true);

    render(
      <CommunitySessionProvider>
        <Probe />
      </CommunitySessionProvider>
    );

    await waitFor(() => expect(claimDevice).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(rotateDeviceToken).toHaveBeenCalledTimes(1));
  });

  it("로그인하지 않은 상태에서는 기기 귀속을 시도하지 않는다", async () => {
    global.fetch = mockSessionFetch(false);

    render(
      <CommunitySessionProvider>
        <Probe />
      </CommunitySessionProvider>
    );

    await waitFor(() => expect(global.fetch).toHaveBeenCalled());
    expect(claimDevice).not.toHaveBeenCalled();
  });

  it("같은 탭 세션에서 다시 마운트되면 귀속을 반복 시도하지 않는다", async () => {
    global.fetch = mockSessionFetch(true);

    const { unmount } = render(
      <CommunitySessionProvider>
        <Probe />
      </CommunitySessionProvider>
    );
    await waitFor(() => expect(claimDevice).toHaveBeenCalledTimes(1));
    unmount();

    render(
      <CommunitySessionProvider>
        <Probe />
      </CommunitySessionProvider>
    );
    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(2));
    expect(claimDevice).toHaveBeenCalledTimes(1);
  });

  it("기기 귀속이 실패해도(다른 계정이 이미 귀속) 토큰은 회전한다", async () => {
    global.fetch = mockSessionFetch(true);
    claimDevice.mockRejectedValue(new Error("DEVICE_ALREADY_CLAIMED"));

    render(
      <CommunitySessionProvider>
        <Probe />
      </CommunitySessionProvider>
    );

    await waitFor(() => expect(claimDevice).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(rotateDeviceToken).toHaveBeenCalledTimes(1));
  });
});
