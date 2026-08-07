import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  getMyBlockedUserIds,
  getMyInteractions,
  getMyRecommendationSet,
  getMySavedNumberMatches,
  saveMySavedNumber,
} from "@/lib/community-client";
import { BrowserApiError } from "@/lib/browser-api";

function jsonResponse(body: unknown) {
  return { ok: true, status: 200, json: () => Promise.resolve(body) };
}

// M-18: community-client.ts의 개인화된 목록/뮤테이션 응답은 이전에 `as T` 캐스트만
// 하고 있어, 형태가 어긋나도 undefined 필드가 화면 깊숙이 조용히 전파됐다. 새로 붙인
// 런타임 가드가 실제로 형태가 다른 응답을 거부하는지 확인한다.
describe("community-client 개인화 응답 런타임 검증", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("getMyInteractions는 배열 필드가 빠진 응답을 거부한다", async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ likedPostIds: [1] }));
    await expect(getMyInteractions([1])).rejects.toThrow(BrowserApiError);
  });

  it("getMyInteractions는 정상 형태 응답을 그대로 반환한다", async () => {
    const body = { likedPostIds: [1], bookmarkedPostIds: [], blockedUserIds: [] };
    global.fetch = vi.fn().mockResolvedValue(jsonResponse(body));
    await expect(getMyInteractions([1])).resolves.toEqual(body);
  });

  it("getMyBlockedUserIds는 숫자가 아닌 원소가 섞인 배열을 거부한다", async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse([1, "2", 3]));
    await expect(getMyBlockedUserIds()).rejects.toThrow(BrowserApiError);
  });

  it("saveMySavedNumber는 savedNumber가 빠진 응답을 거부한다", async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ created: true }));
    await expect(saveMySavedNumber([1, 2, 3, 4, 5, 6])).rejects.toThrow(BrowserApiError);
  });

  it("getMySavedNumberMatches는 원소 형태가 어긋난 배열을 거부한다", async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse([{ round: 1189 }]));
    await expect(getMySavedNumberMatches("latest")).rejects.toThrow(BrowserApiError);
  });

  it("getMyRecommendationSet은 strategy가 빠진 응답을 거부한다", async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ id: 1, items: [] }));
    await expect(getMyRecommendationSet(1)).rejects.toThrow(BrowserApiError);
  });
});
