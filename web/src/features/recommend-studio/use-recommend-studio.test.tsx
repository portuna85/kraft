import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useRecommendStudio } from "./use-recommend-studio";

const recommendNumbers = vi.fn();
const recommendNumbersForAccount = vi.fn();
const saveNumbersToAccount = vi.fn();
const saveNumbersToDevice = vi.fn();

vi.mock("@/entities/recommendation/api", () => ({
  recommendNumbers: (...args: unknown[]) => recommendNumbers(...args),
  recommendNumbersForAccount: (...args: unknown[]) => recommendNumbersForAccount(...args),
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
  recommendNumbersForAccount.mockReset();
  saveNumbersToAccount.mockReset();
  saveNumbersToDevice.mockReset();
});

describe("추천 스튜디오", () => {
  it("마운트만으로는 추천 요청을 보내지 않는다 (F-P0-6/7, T-3)", () => {
    renderHook(() => useRecommendStudio({ loggedIn: false, sessionReady: true }));

    // 화면에 들어오는 것만으로 POST가 나가면 원하지 않은 추천이 이력에 쌓이고,
    // 크롤러 방문마다 서버가 조합을 생성한다.
    expect(recommendNumbers).not.toHaveBeenCalled();
  });

  it("생성 버튼에 해당하는 호출에서만 요청한다", async () => {
    recommendNumbers.mockResolvedValue(response([[1, 2, 3, 4, 5, 6]]));
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

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

    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

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
    const { result } = renderHook(() => useRecommendStudio({ loggedIn: true, sessionReady: true }));

    await act(async () => {
      await result.current.save(0, [1, 2, 3, 4, 5, 6]);
    });

    // claim 완료를 기다렸다면 실패한 사용자의 저장이 익명 스코프로 떨어진다.
    expect(saveNumbersToAccount).toHaveBeenCalledTimes(1);
    expect(saveNumbersToDevice).not.toHaveBeenCalled();
  });

  it("익명이면 기기 스코프로 저장한다", async () => {
    saveNumbersToDevice.mockResolvedValue({ savedNumber: { id: 1 }, created: true });
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

    await act(async () => {
      await result.current.save(0, [1, 2, 3, 4, 5, 6]);
    });

    expect(saveNumbersToDevice).toHaveBeenCalledTimes(1);
    expect(saveNumbersToAccount).not.toHaveBeenCalled();
  });

  it("KF-01: 로그인 상태면 계정 소유로 생성한다", async () => {
    recommendNumbersForAccount.mockResolvedValue(response([[1, 2, 3, 4, 5, 6]]));
    const { result } = renderHook(() => useRecommendStudio({ loggedIn: true, sessionReady: true }));

    await act(async () => {
      await result.current.generate();
    });

    expect(recommendNumbersForAccount).toHaveBeenCalledTimes(1);
    expect(recommendNumbers).not.toHaveBeenCalled();
    expect(result.current.state.status).toBe("ready");
  });

  it("KF-09: 세션이 미확정이면 generate()가 요청을 보내지 않는다", async () => {
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: false }),
    );

    await act(async () => {
      await result.current.generate();
    });

    expect(recommendNumbers).not.toHaveBeenCalled();
    expect(recommendNumbersForAccount).not.toHaveBeenCalled();
    expect(result.current.state.status).toBe("idle");
  });

  it("KF-09: 세션이 미확정이면 save()가 요청을 보내지 않는다", async () => {
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: true, sessionReady: false }),
    );

    await act(async () => {
      await result.current.save(0, [1, 2, 3, 4, 5, 6]);
    });

    expect(saveNumbersToAccount).not.toHaveBeenCalled();
    expect(saveNumbersToDevice).not.toHaveBeenCalled();
  });

  it("중복 저장을 오류가 아니라 응답 필드로 다룬다", async () => {
    // 백엔드는 중복 저장 시 오류가 아니라 200 + created:false를 돌려준다
    // (SavedNumbersController) — 409를 기다리면 이 상태를 영영 놓친다.
    saveNumbersToDevice.mockResolvedValue({ savedNumber: { id: 1 }, created: false });
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

    await act(async () => {
      await result.current.save(0, [1, 2, 3, 4, 5, 6]);
    });

    await waitFor(() => expect(result.current.saveOutcomes.get(0)).toEqual({ kind: "duplicate" }));
  });

  it("실제 요청 실패는 실패 상태로 남는다", async () => {
    saveNumbersToDevice.mockRejectedValue(new Error("network down"));
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

    await act(async () => {
      await result.current.save(0, [1, 2, 3, 4, 5, 6]);
    });

    await waitFor(() =>
      expect(result.current.saveOutcomes.get(0)).toMatchObject({ kind: "failed" }),
    );
  });
});

describe("번호 선택 모드", () => {
  it("기본 모드는 고정이다", () => {
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );
    expect(result.current.selectionMode).toBe("locked");
  });

  it("현재 모드에서 누르면 선택되고, 같은 모드에서 다시 누르면 해제된다", () => {
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

    act(() => result.current.toggleNumber(7));
    expect(result.current.lockedNumbers).toEqual([7]);

    act(() => result.current.toggleNumber(7));
    expect(result.current.lockedNumbers).toEqual([]);
  });

  it("반대 모드에 있던 번호를 누르면 반대 모드에서 빠지고 현재 모드로 옮겨간다", () => {
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

    act(() => result.current.toggleNumber(7)); // locked 모드에서 고정
    expect(result.current.lockedNumbers).toEqual([7]);

    act(() => result.current.setSelectionMode("excluded"));
    act(() => result.current.toggleNumber(7)); // excluded 모드에서 같은 번호

    expect(result.current.lockedNumbers).toEqual([]);
    expect(result.current.excludedNumbers).toEqual([7]);
  });

  it("고정은 5개를 넘지 않는다", () => {
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

    for (const value of [1, 2, 3, 4, 5, 6]) {
      act(() => result.current.toggleNumber(value));
    }

    expect(result.current.lockedNumbers).toEqual([1, 2, 3, 4, 5]);
  });

  it("제외 모드에는 고정 상한이 적용되지 않는다", () => {
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );
    act(() => result.current.setSelectionMode("excluded"));

    for (const value of [1, 2, 3, 4, 5, 6]) {
      act(() => result.current.toggleNumber(value));
    }

    expect(result.current.excludedNumbers).toEqual([1, 2, 3, 4, 5, 6]);
  });
});

describe("I-28: 낡은 결과 표시", () => {
  it("생성 직후에는 낡지 않은 상태다", async () => {
    recommendNumbers.mockResolvedValue(response([[1, 2, 3, 4, 5, 6]]));
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

    await act(async () => {
      await result.current.generate();
    });

    expect(result.current.isStale).toBe(false);
  });

  it("결과가 나온 뒤 고정 번호를 바꾸면 낡은 상태가 된다", async () => {
    recommendNumbers.mockResolvedValue(response([[1, 2, 3, 4, 5, 6]]));
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

    await act(async () => {
      await result.current.generate();
    });
    act(() => result.current.toggleNumber(7));

    expect(result.current.isStale).toBe(true);
  });

  it("같은 조건으로 다시 만들면 낡은 상태가 풀린다", async () => {
    recommendNumbers.mockResolvedValue(response([[1, 2, 3, 4, 5, 6]]));
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

    await act(async () => {
      await result.current.generate();
    });
    act(() => result.current.toggleNumber(7));
    expect(result.current.isStale).toBe(true);

    await act(async () => {
      await result.current.generate();
    });

    expect(result.current.isStale).toBe(false);
  });

  it("결과가 없는 동안에는 낡은 상태를 표시하지 않는다", () => {
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

    act(() => result.current.toggleNumber(7));

    expect(result.current.isStale).toBe(false);
  });
});

describe("조합 개수", () => {
  it("1~10 범위로 잘라낸다", () => {
    const { result } = renderHook(() =>
      useRecommendStudio({ loggedIn: false, sessionReady: true }),
    );

    act(() => result.current.setCount(0));
    expect(result.current.count).toBe(1);

    act(() => result.current.setCount(99));
    expect(result.current.count).toBe(10);
  });
});
