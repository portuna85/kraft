import { describe, expect, it, beforeEach, vi } from "vitest";

describe("디바이스 토큰 조회", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetModules();
  });

  it("서버 렌더링 환경에서는 빈 문자열을 반환한다", async () => {
    // Simulate SSR by temporarily hiding window
    const win = globalThis.window;
    // @ts-expect-error — intentional SSR simulation
    delete globalThis.window;
    const { getDeviceToken } = await import("@/lib/device-token");
    expect(getDeviceToken()).toBe("");
    globalThis.window = win;
  });

  it("최초 호출 시 토큰을 생성하고 캐시한다", async () => {
    const { getDeviceToken } = await import("@/lib/device-token");
    const token = getDeviceToken();
    expect(token).toBeTruthy();
    expect(token.length).toBeGreaterThanOrEqual(32);
    // Second call must return the same token (cached in localStorage)
    expect(getDeviceToken()).toBe(token);
  });

  it("모듈을 다시 로드해도 저장된 토큰을 반환한다", async () => {
    const { getDeviceToken } = await import("@/lib/device-token");
    const first = getDeviceToken();

    vi.resetModules();
    const { getDeviceToken: getDeviceToken2 } = await import("@/lib/device-token");
    expect(getDeviceToken2()).toBe(first);
  });

  it("브라우저 난수 식별자를 쓸 수 없으면 자체 토큰 생성으로 대체한다", async () => {
    const originalDescriptor = Object.getOwnPropertyDescriptor(crypto, "randomUUID");
    Object.defineProperty(crypto, "randomUUID", { value: undefined, configurable: true, writable: true });
    try {
      const { getDeviceToken } = await import("@/lib/device-token");
      const token = getDeviceToken();
      expect(token).toHaveLength(64); // 32 bytes × 2 hex chars
    } finally {
      if (originalDescriptor) {
        Object.defineProperty(crypto, "randomUUID", originalDescriptor);
      }
    }
  });

  it("회전하면 새 토큰을 생성해 이전 토큰을 대체한다", async () => {
    const { getDeviceToken, rotateDeviceToken } = await import("@/lib/device-token");
    const original = getDeviceToken();

    const rotated = rotateDeviceToken();

    expect(rotated).toBeTruthy();
    expect(rotated).not.toBe(original);
    expect(getDeviceToken()).toBe(rotated);
  });

  it("서버 렌더링 환경에서 회전을 호출하면 빈 문자열을 반환한다", async () => {
    const win = globalThis.window;
    // @ts-expect-error — intentional SSR simulation
    delete globalThis.window;
    const { rotateDeviceToken } = await import("@/lib/device-token");
    expect(rotateDeviceToken()).toBe("");
    globalThis.window = win;
  });
});
