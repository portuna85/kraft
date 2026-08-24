import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { DEVICE_TOKEN_STORAGE_KEY } from "@/shared/api/device-token";
import { headersOf, initOf, jsonResponse, mockFetch, urlOf } from "@/shared/api/test/fetch-mock";

import {
  deleteAccountSavedNumber,
  deleteDeviceSavedNumber,
  listAccountSavedNumbers,
  listDeviceSavedNumbers,
  matchAccountSavedNumbers,
  matchDeviceSavedNumbers,
  saveNumbersToAccount,
  saveNumbersToDevice,
} from "./api";

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
  window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "device-abc");
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const savedNumber = {
  id: 1,
  numbers: [1, 2, 3, 4, 5, 6],
  label: null,
  source: "manual",
  createdAt: "2025-01-01T00:00:00Z",
};

describe("익명 vs 계정 스코프 — 엔드포인트가 다르다(claim 이후 device 경로로 못 찾음)", () => {
  it("listDeviceSavedNumbers는 device 경로 + 토큰을 쓴다", async () => {
    const spy = mockFetch(jsonResponse([savedNumber]));

    await listDeviceSavedNumbers();

    expect(urlOf(spy)).toBe("/api/v1/saved");
    expect(headersOf(spy)["X-Device-Token"]).toBe("device-abc");
  });

  it("listAccountSavedNumbers는 account 경로를 쓰고 토큰이 없다", async () => {
    const spy = mockFetch(jsonResponse([savedNumber]));

    await listAccountSavedNumbers();

    expect(urlOf(spy)).toBe("/api/v1/community/me/saved-numbers");
    expect(headersOf(spy)["X-Device-Token"]).toBeUndefined();
  });

  it("saveNumbersToDevice/saveNumbersToAccount가 서로 다른 경로로 POST한다", async () => {
    const deviceSpy = mockFetch(jsonResponse({ savedNumber, created: true }));
    await saveNumbersToDevice([1, 2, 3, 4, 5, 6]);
    expect(urlOf(deviceSpy)).toBe("/api/v1/saved");
    expect(headersOf(deviceSpy)["X-Device-Token"]).toBe("device-abc");

    vi.unstubAllGlobals();

    const accountSpy = mockFetch(jsonResponse({ savedNumber, created: true }));
    await saveNumbersToAccount([1, 2, 3, 4, 5, 6]);
    expect(urlOf(accountSpy)).toBe("/api/v1/community/me/saved-numbers");
    expect(headersOf(accountSpy)["X-Device-Token"]).toBeUndefined();
  });

  it("deleteDeviceSavedNumber/deleteAccountSavedNumber가 서로 다른 경로로 DELETE한다", async () => {
    const deviceSpy = mockFetch(new Response(null, { status: 204 }));
    await deleteDeviceSavedNumber(9);
    expect(urlOf(deviceSpy)).toBe("/api/v1/saved/9");

    vi.unstubAllGlobals();

    const accountSpy = mockFetch(new Response(null, { status: 204 }));
    await deleteAccountSavedNumber(9);
    expect(urlOf(accountSpy)).toBe("/api/v1/community/me/saved-numbers/9");
  });
});

describe("회차 대조 — round 파라미터 인코딩", () => {
  it("matchDeviceSavedNumbers가 round를 encodeURIComponent로 인코딩한다", async () => {
    const spy = mockFetch(jsonResponse([]));

    await matchDeviceSavedNumbers("1150&extra=1");

    expect(urlOf(spy)).toBe("/api/v1/saved/matches?round=1150%26extra%3D1");
  });

  it("matchAccountSavedNumbers도 같은 방식으로 인코딩한다", async () => {
    const spy = mockFetch(jsonResponse([]));

    await matchAccountSavedNumbers("1150&extra=1");

    expect(urlOf(spy)).toBe(
      "/api/v1/community/me/saved-numbers/matches?round=1150%26extra%3D1",
    );
  });
});
