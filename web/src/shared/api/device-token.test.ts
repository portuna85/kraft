import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  DEVICE_TOKEN_STORAGE_KEY,
  ensureDeviceToken,
  readDeviceToken,
  rotateDeviceTokenAfterSuccessfulClaim,
} from "./device-token";

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("ensureDeviceToken", () => {
  it("토큰이 없으면 만들어 저장한다", () => {
    const token = ensureDeviceToken();
    expect(token).not.toBeNull();
    expect(window.localStorage.getItem(DEVICE_TOKEN_STORAGE_KEY)).toBe(token);
  });

  it("이미 있는 토큰은 그대로 재사용한다", () => {
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "existing-token");
    expect(ensureDeviceToken()).toBe("existing-token");
  });

  it("localStorage 접근이 실패하면(프라이빗 모드 등) null을 돌려준다", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("private mode");
    });
    expect(ensureDeviceToken()).toBeNull();
  });
});

describe("readDeviceToken", () => {
  it("있는 토큰을 읽는다", () => {
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "existing-token");
    expect(readDeviceToken()).toBe("existing-token");
  });

  it("없으면 새로 만들지 않고 null을 돌려준다", () => {
    expect(readDeviceToken()).toBeNull();
    expect(window.localStorage.getItem(DEVICE_TOKEN_STORAGE_KEY)).toBeNull();
  });

  it("localStorage 접근이 실패하면 null을 돌려준다", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("private mode");
    });
    expect(readDeviceToken()).toBeNull();
  });
});

describe("rotateDeviceTokenAfterSuccessfulClaim", () => {
  it("기존 토큰을 새 값으로 교체한다", () => {
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "old-token");
    const rotated = rotateDeviceTokenAfterSuccessfulClaim();
    expect(rotated).not.toBe("old-token");
    expect(rotated).not.toBeNull();
    expect(window.localStorage.getItem(DEVICE_TOKEN_STORAGE_KEY)).toBe(rotated);
  });

  it("localStorage 접근이 실패하면 null을 돌려준다", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("private mode");
    });
    expect(rotateDeviceTokenAfterSuccessfulClaim()).toBeNull();
  });
});
