import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { HttpResponse, http } from "msw";

import { server } from "../../../tests/msw/server";

import { deleteDeviceSavedNumber, listDeviceSavedNumbers, saveNumbersToDevice } from "./api";

/**
 * MSW를 실제로 거치는 통합 테스트
 *
 * 저장 응답 계약(`{savedNumber, created}`)은 예전에 스키마 자체가 어긋나 있던 자리다
 * (Phase 5에서 발견·수정). 여기서 실제 fetch → MSW → Valibot 경로로 created:false
 * 중복 응답까지 왕복시켜, 핸들러가 계약을 계속 만족하는지 확인한다.
 */
describe("보관함 API — MSW 통합", () => {
  beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
  afterEach(() => server.resetHandlers());
  afterAll(() => server.close());

  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
  });

  it("기기 스코프 목록을 받아 스키마 검증까지 통과한다", async () => {
    const result = await listDeviceSavedNumbers();
    expect(result).toHaveLength(1);
    expect(result[0]?.numbers).toHaveLength(6);
  });

  it("신규 저장은 created:true를 받는다", async () => {
    const result = await saveNumbersToDevice([1, 2, 3, 4, 5, 6]);
    expect(result.created).toBe(true);
    expect(result.savedNumber.numbers).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it("중복 저장(created:false)도 스키마를 통과한다", async () => {
    server.use(
      http.post("/api/v1/saved", () =>
        HttpResponse.json({
          savedNumber: {
            id: 1,
            numbers: [1, 2, 3, 4, 5, 6],
            label: null,
            source: "recommend",
            createdAt: "2026-08-01T12:00:00Z",
          },
          created: false,
        }),
      ),
    );

    const result = await saveNumbersToDevice([1, 2, 3, 4, 5, 6]);
    expect(result.created).toBe(false);
  });

  it("삭제는 204를 받고 null을 돌려준다", async () => {
    await expect(deleteDeviceSavedNumber(1)).resolves.toBeNull();
  });
});
