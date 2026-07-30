import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";
import {
  CommunitySessionProvider,
  isSessionScopedPath,
  useCommunitySession,
} from "@/lib/community-session-provider";

const claimDevice = vi.fn();
const rotateDeviceToken = vi.fn();
const mockUsePathname = vi.fn(() => "/community");

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

// KF-05: 이 테스트 파일의 기존 케이스는 전부 "세션이 실제로 필요한 라우트(/community)"를
// 전제로 한 기기 귀속 검증이라, 기본값을 스코프 안으로 고정해 기존 단언을 그대로 유지한다.
// 스코프 자체의 동작(공개 페이지 미호출 등)은 아래 별도 describe에서 pathname을 바꿔가며 검증한다.
vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
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
    mockUsePathname.mockReset().mockReturnValue("/community");
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

  it("F-P0-10: 기기 귀속이 실패하면(다른 계정이 이미 귀속 등) 토큰을 회전하지 않는다", async () => {
    global.fetch = mockSessionFetch(true);
    claimDevice.mockRejectedValue(new Error("DEVICE_ALREADY_CLAIMED"));

    render(
      <CommunitySessionProvider>
        <Probe />
      </CommunitySessionProvider>
    );

    await waitFor(() => expect(claimDevice).toHaveBeenCalledTimes(1));
    expect(rotateDeviceToken).not.toHaveBeenCalled();
  });

  it("F-P0-10: 귀속 실패 후 재마운트되면(플래그가 세팅되지 않았으므로) 귀속을 재시도한다", async () => {
    global.fetch = mockSessionFetch(true);
    claimDevice.mockRejectedValue(new Error("DEVICE_ALREADY_CLAIMED"));

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
    await waitFor(() => expect(claimDevice).toHaveBeenCalledTimes(2));
  });
});

describe("KF-05: 세션 조회 라우트 스코핑", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    claimDevice.mockReset();
    rotateDeviceToken.mockReset();
    mockUsePathname.mockReset();
    window.sessionStorage.clear();
  });

  it.each(["/", "/stats", "/frequency", "/companion", "/info/some-slug"])(
    "%s는 세션 스코프 밖이다",
    (path) => {
      expect(isSessionScopedPath(path)).toBe(false);
    }
  );

  it.each(["/community", "/community/posts/1", "/saved", "/saved/history"])(
    "%s는 세션 스코프 안이다",
    (path) => {
      expect(isSessionScopedPath(path)).toBe(true);
    }
  );

  it("공개 페이지(스코프 밖)에서는 세션 API를 호출하지 않는다", async () => {
    mockUsePathname.mockReturnValue("/stats");
    global.fetch = mockSessionFetch(true);

    render(
      <CommunitySessionProvider>
        <Probe />
      </CommunitySessionProvider>
    );

    await waitFor(() => expect(rotateDeviceToken).not.toHaveBeenCalled());
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it("커뮤니티 페이지(스코프 안)에서는 세션 API를 정확히 1회 호출한다", async () => {
    mockUsePathname.mockReturnValue("/community");
    global.fetch = mockSessionFetch(true);

    render(
      <CommunitySessionProvider>
        <Probe />
      </CommunitySessionProvider>
    );

    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));
  });
});
