import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/shared/api/error";

import { collectLatest, collectRound, getOpsLogs, getOpsSummary, upsertRound } from "./api";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function mockFetch(response: Response) {
  const spy = vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", spy);
  return spy;
}

function headersOf(spy: ReturnType<typeof vi.fn>): Record<string, string> {
  const init = spy.mock.calls[0]?.[1] as RequestInit | undefined;
  return (init?.headers ?? {}) as Record<string, string>;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

const summaryBody = {
  service: "kraft-lotto",
  timezone: "Asia/Seoul",
  status: "OK",
  latestRound: 1150,
  latestDrawDate: "2025-01-04",
  checkedAt: "2025-01-05T00:00:00Z",
  fresh: true,
};

const winningNumberBody = {
  round: 1150,
  drawDate: "2025-01-04",
  numbers: [1, 2, 3, 4, 5, 6],
  bonusNumber: 7,
  firstPrizeAmount: 1_000_000_000,
  secondPrize: 0,
  secondWinners: 0,
  totalSales: 0,
  firstAccumAmount: 0,
};

describe("getOpsSummary", () => {
  it("경로와 토큰 헤더를 붙여 요약을 조회한다", async () => {
    const spy = mockFetch(jsonResponse(summaryBody));

    const result = await getOpsSummary("secret-token");

    expect(spy.mock.calls[0]?.[0]).toBe("/ops-api/summary");
    expect(headersOf(spy)["X-Ops-Token"]).toBe("secret-token");
    expect(result).toEqual(summaryBody);
  });

  it("토큰이 틀리면 401을 그대로 노출한다", async () => {
    mockFetch(
      jsonResponse({ code: "OPS_UNAUTHORIZED", message: "토큰이 올바르지 않습니다." }, 401),
    );

    const error = await getOpsSummary("wrong-token").catch((e: unknown) => e as ApiError);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ code: "OPS_UNAUTHORIZED", status: 401 });
  });
});

describe("getOpsLogs", () => {
  it("page·size를 쿼리스트링으로 붙인다", async () => {
    const spy = mockFetch(
      jsonResponse({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    );

    await getOpsLogs("secret-token", { page: 0, size: 20 });

    expect(spy.mock.calls[0]?.[0]).toBe("/ops-api/logs?page=0&size=20");
  });
});

describe("collectLatest", () => {
  it("POST로 최신 회차 수집을 트리거한다", async () => {
    const spy = mockFetch(jsonResponse(winningNumberBody));

    const result = await collectLatest("secret-token");

    const init = spy.mock.calls[0]?.[1] as RequestInit;
    expect(init.method).toBe("POST");
    expect(result).toEqual(winningNumberBody);
  });

  it("TD-001: edge 20초보다 긴 22초 타임아웃으로 abort signal을 구성한다", async () => {
    mockFetch(jsonResponse(winningNumberBody));
    const timeoutSpy = vi.spyOn(AbortSignal, "timeout");

    await collectLatest("secret-token");

    expect(timeoutSpy).toHaveBeenCalledWith(22_000);
  });
});

describe("collectRound", () => {
  it("지정 회차 경로로 POST한다", async () => {
    const spy = mockFetch(jsonResponse(winningNumberBody));

    await collectRound("secret-token", 1150);

    expect(spy.mock.calls[0]?.[0]).toBe("/ops-api/collect/1150");
  });

  it("TD-001: edge 20초보다 긴 22초 타임아웃으로 abort signal을 구성한다", async () => {
    mockFetch(jsonResponse(winningNumberBody));
    const timeoutSpy = vi.spyOn(AbortSignal, "timeout");

    await collectRound("secret-token", 1150);

    expect(timeoutSpy).toHaveBeenCalledWith(22_000);
  });
});

describe("getOpsSummary — 타임아웃 회귀 방지", () => {
  it("수집 외 ops 호출은 기본 5초 타임아웃을 그대로 쓴다", async () => {
    mockFetch(jsonResponse(summaryBody));
    const timeoutSpy = vi.spyOn(AbortSignal, "timeout");

    await getOpsSummary("secret-token");

    expect(timeoutSpy).toHaveBeenCalledWith(5_000);
  });
});

describe("upsertRound", () => {
  it("본문을 JSON으로 실어 회차를 적재한다", async () => {
    const spy = mockFetch(jsonResponse(winningNumberBody));

    await upsertRound("secret-token", {
      round: 1150,
      drawDate: "2025-01-04",
      numbers: [1, 2, 3, 4, 5, 6],
      bonusNumber: 7,
      firstPrizeAmount: 1_000_000_000,
    });

    const init = spy.mock.calls[0]?.[1] as { body?: string };
    expect(JSON.parse(init.body ?? "{}")).toMatchObject({ round: 1150, bonusNumber: 7 });
  });
});
