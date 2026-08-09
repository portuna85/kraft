import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useRecommendStudio } from "./use-recommend-studio";

const recommendNumbers = vi.fn();
const saveNumbersToAccount = vi.fn();
const saveNumbersToDevice = vi.fn();

vi.mock("@/entities/recommendation/api", () => ({
  recommendNumbers: (...args: unknown[]) => recommendNumbers(...args),
}));

vi.mock("@/entities/saved-number/api", () => ({
  saveNumbersToAccount: (...args: unknown[]) => saveNumbersToAccount(...args),
  saveNumbersToDevice: (...args: unknown[]) => saveNumbersToDevice(...args),
}));

function response(numbers: number[][]) {
  return {
    recommendations: numbers,
    strategy: "balanced" as const,
    algorithmVersion: "v1",
    historyThroughRound: 1150,
    historicalExclusionApplied: false,
    exclusionPolicyVersion: "v1",
    setId: null,
    items: null,
    createdAt: null,
  };
}

beforeEach(() => {
  recommendNumbers.mockReset();
  saveNumbersToAccount.mockReset();
  saveNumbersToDevice.mockReset();
});

describe("추천 스튜디오", () => {
  it("마운트만으로는 추천 요청을 보내지 않는다 (F-P0-6/7, T-3)", () => {
    renderHook(() => useRecommendStudio({ loggedIn: false }));

    // 화면에 들어오는 것만으로 POST가 나가면 원하지 않은 추천이 이력에 쌓이고,
    // 크롤러 방문마다 서버가 조합을 생성한다.
    expect(recommendNumbers).not.toHaveBeenCalled();
  });

  it("생성 버튼에 해당하는 호출에서만 요청한다", async () => {
    recommendNumbers.mockResolvedValue(response([[1, 2, 3, 4, 5, 6]]));
    const { result } = renderHook(() => useRecommendStudio({ loggedIn: false }));

    await act(async () => {
      await result.current.generate();
    });

    expect(recommendNumbers).toHaveBeenCalledTimes(1);
    expect(result.current.state.status).toBe("ready");
  });

  it("낡은 응답을 버리고 최신 요청의 결과만 남긴다", async () => {
    let resolveFirst: ((value: unknown) => void) | undefined;
    recommendNumbers
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockResolvedValueOnce(response([[7, 8, 9, 10, 11, 12]]));

    const { result } = renderHook(() => useRecommendStudio({ loggedIn: false }));

    let firstCall: Promise<void>;
    act(() => {
      firstCall = result.current.generate();
    });
    await act(async () => {
      await result.current.generate();
    });

    // 먼저 보낸 요청이 나중에 도착해도 화면을 덮어쓰지 않아야 한다.
    await act(async () => {
      resolveFirst?.(response([[1, 2, 3, 4, 5, 6]]));
      await firstCall;
    });

    expect(result.current.state).toMatchObject({
      status: "ready",
      result: { recommendations: [[7, 8, 9, 10, 11, 12]] },
    });
  });

  it("로그인 상태면 계정으로 저장한다 (C-2)", async () => {
    saveNumbersToAccount.mockResolvedValue({ savedNumber: { id: 1 }, created: true });
    const { result } = renderHook(() => useRecommendStudio({ loggedIn: true }));

    await act(async () => {
      await result.current.save(0, [1, 2, 3, 4, 5, 6]);
    });

    // claim 완료를 기다렸다면 실패한 사용자의 저장이 익명 스코프로 떨어진다.
    expect(saveNumbersToAccount).toHaveBeenCalledTimes(1);
    expect(saveNumbersToDevice).not.toHaveBeenCalled();
  });

  it("익명이면 기기 스코프로 저장한다", async () => {
    saveNumbersToDevice.mockResolvedValue({ savedNumber: { id: 1 }, created: true });
    const { result } = renderHook(() => useRecommendStudio({ loggedIn: false }));

    await act(async () => {
      await result.current.save(0, [1, 2, 3, 4, 5, 6]);
    });

    expect(saveNumbersToDevice).toHaveBeenCalledTimes(1);
    expect(saveNumbersToAccount).not.toHaveBeenCalled();
  });

  it("중복 저장을 오류가 아니라 응답 필드로 다룬다", async () => {
    // 백엔드는 중복 저장 시 오류가 아니라 200 + created:false를 돌려준다
    // (SavedNumbersController) — 409를 기다리면 이 상태를 영영 놓친다.
    saveNumbersToDevice.mockResolvedValue({ savedNumber: { id: 1 }, created: false });
    const { result } = renderHook(() => useRecommendStudio({ loggedIn: false }));

    await act(async () => {
      await result.current.save(0, [1, 2, 3, 4, 5, 6]);
    });

    await waitFor(() => expect(result.current.saveOutcomes.get(0)).toEqual({ kind: "duplicate" }));
  });

  it("실제 요청 실패는 실패 상태로 남는다", async () => {
    saveNumbersToDevice.mockRejectedValue(new Error("network down"));
    const { result } = renderHook(() => useRecommendStudio({ loggedIn: false }));

    await act(async () => {
      await result.current.save(0, [1, 2, 3, 4, 5, 6]);
    });

    await waitFor(() =>
      expect(result.current.saveOutcomes.get(0)).toMatchObject({ kind: "failed" }),
    );
  });
});

describe("번호 3단 토글", () => {
  it("none → locked → excluded → none 순으로 바뀐다", () => {
    const { result } = renderHook(() => useRecommendStudio({ loggedIn: false }));

    act(() => result.current.toggleNumber(7));
    expect(result.current.lockedNumbers).toEqual([7]);

    act(() => result.current.toggleNumber(7));
    expect(result.current.excludedNumbers).toEqual([7]);
    expect(result.current.lockedNumbers).toEqual([]);

    act(() => result.current.toggleNumber(7));
    expect(result.current.lockedNumbers).toEqual([]);
    expect(result.current.excludedNumbers).toEqual([]);
  });

  it("고정은 5개를 넘지 않는다", () => {
    const { result } = renderHook(() => useRecommendStudio({ loggedIn: false }));

    for (const value of [1, 2, 3, 4, 5, 6]) {
      act(() => result.current.toggleNumber(value));
    }

    expect(result.current.lockedNumbers).toEqual([1, 2, 3, 4, 5]);
  });
});

describe("조합 개수", () => {
  it("1~10 범위로 잘라낸다", () => {
    const { result } = renderHook(() => useRecommendStudio({ loggedIn: false }));

    act(() => result.current.setCount(0));
    expect(result.current.count).toBe(1);

    act(() => result.current.setCount(99));
    expect(result.current.count).toBe(10);
  });
});
