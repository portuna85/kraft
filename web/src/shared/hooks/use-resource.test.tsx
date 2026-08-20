import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/shared/api/error";

import { invalidateResource, resetResourceCacheForTests, useResource } from "./use-resource";

afterEach(() => {
  resetResourceCacheForTests();
});

function Probe({
  cacheKey,
  load,
  ttlMs,
}: {
  cacheKey: string | null;
  load: (signal: AbortSignal) => Promise<string>;
  ttlMs?: number;
}) {
  const state = useResource(cacheKey, load, ttlMs === undefined ? {} : { ttlMs });

  if (state.status === "idle") return <p>대기</p>;
  if (state.status === "loading") return <p>불러오는 중</p>;
  if (state.status === "error") {
    return (
      <div>
        <p>실패: {state.error.message}</p>
        <button type="button" onClick={state.retry}>
          다시 시도
        </button>
      </div>
    );
  }
  return <p>{state.data}</p>;
}

describe("useResource", () => {
  it("key가 null이면 조회하지 않는다", () => {
    const load = vi.fn();
    render(<Probe cacheKey={null} load={load} />);

    expect(screen.getByText("대기")).toBeInTheDocument();
    expect(load).not.toHaveBeenCalled();
  });

  it("성공 데이터를 돌려준다", async () => {
    render(<Probe cacheKey="a" load={() => Promise.resolve("값")} />);
    expect(await screen.findByText("값")).toBeInTheDocument();
  });

  it("같은 키를 동시에 보는 컴포넌트가 요청을 한 번만 보낸다", async () => {
    const load = vi.fn().mockResolvedValue("값");
    render(
      <>
        <Probe cacheKey="dedupe" load={load} />
        <Probe cacheKey="dedupe" load={load} />
      </>,
    );

    await waitFor(() => expect(screen.getAllByText("값")).toHaveLength(2));
    expect(load).toHaveBeenCalledTimes(1);
  });

  it("TTL 안에 다시 마운트되면 캐시를 쓴다", async () => {
    const load = vi.fn().mockResolvedValue("값");

    const first = render(<Probe cacheKey="ttl" load={load} ttlMs={10_000} />);
    expect(await screen.findByText("값")).toBeInTheDocument();
    first.unmount();

    render(<Probe cacheKey="ttl" load={load} ttlMs={10_000} />);
    expect(await screen.findByText("값")).toBeInTheDocument();
    expect(load).toHaveBeenCalledTimes(1);
  });

  it("TTL이 지나면 다시 조회한다", async () => {
    const load = vi.fn().mockResolvedValue("값");

    const first = render(<Probe cacheKey="stale" load={load} ttlMs={0} />);
    expect(await screen.findByText("값")).toBeInTheDocument();
    first.unmount();

    render(<Probe cacheKey="stale" load={load} ttlMs={0} />);
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
  });

  it("실패를 빈 데이터로 축약하지 않고 오류 상태로 드러낸다", async () => {
    const load = vi.fn().mockRejectedValue(new ApiError("server", "서버 오류"));
    render(<Probe cacheKey="fail" load={load} />);

    expect(await screen.findByText("실패: 서버 오류")).toBeInTheDocument();
  });

  it("실패는 캐시에 남기지 않아 재시도가 실제로 다시 요청한다", async () => {
    const load = vi
      .fn()
      .mockRejectedValueOnce(new ApiError("network", "연결 실패"))
      .mockResolvedValue("복구됨");

    render(<Probe cacheKey="retry" load={load} ttlMs={10_000} />);
    await screen.findByText("실패: 연결 실패");

    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("복구됨")).toBeInTheDocument();
    expect(load).toHaveBeenCalledTimes(2);
  });

  it("무효화하면 접두사가 일치하는 구독자가 다시 조회한다", async () => {
    const load = vi.fn().mockResolvedValueOnce("이전").mockResolvedValue("이후");

    render(<Probe cacheKey="me:saved" load={load} ttlMs={10_000} />);
    expect(await screen.findByText("이전")).toBeInTheDocument();

    act(() => {
      invalidateResource("me:");
    });

    expect(await screen.findByText("이후")).toBeInTheDocument();
  });

  it("무효화 접두사가 다르면 재조회하지 않는다", async () => {
    const load = vi.fn().mockResolvedValue("값");

    render(<Probe cacheKey="me:saved" load={load} ttlMs={10_000} />);
    expect(await screen.findByText("값")).toBeInTheDocument();

    act(() => {
      invalidateResource("community:");
    });

    expect(load).toHaveBeenCalledTimes(1);
  });

  it("마지막 구독자가 사라지면 진행 중 요청을 끊는다", async () => {
    let observed: AbortSignal | undefined;
    const load = (signal: AbortSignal) => {
      observed = signal;
      return new Promise<string>(() => {});
    };

    const view = render(<Probe cacheKey="abort" load={load} ttlMs={10_000} />);
    await screen.findByText("불러오는 중");
    view.unmount();

    expect(observed?.aborted).toBe(true);
  });

  it("TD-012: 진행 중 요청을 무효화하면 그 요청을 끊는다", async () => {
    let observed: AbortSignal | undefined;
    const load = (signal: AbortSignal) => {
      observed = signal;
      return new Promise<string>(() => {});
    };

    render(<Probe cacheKey="me:pending" load={load} ttlMs={10_000} />);
    await screen.findByText("불러오는 중");

    // 무효화가 (act로 인해 동기 flush되는) 재구독 재조회를 곧장 트리거해 `load`가
    // 다시 불릴 수 있다 — 그러면 `observed`가 새 시도의 signal로 덮어써진다. 끊겨야
    // 할 대상은 이 첫 시도의 signal이므로 무효화 전에 따로 붙잡아 둔다.
    const firstSignal = observed;

    act(() => {
      invalidateResource("me:");
    });

    expect(firstSignal?.aborted).toBe(true);
  });

  it("TD-012: 무효화 후 새 시도는 끊긴 이전 요청의 응답으로 오염되지 않는다", async () => {
    let firstReject: ((reason: unknown) => void) | undefined;
    const load = vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise<string>((_resolve, reject) => {
            firstReject = reject;
          }),
      )
      .mockResolvedValue("새 값");

    render(<Probe cacheKey="me:race" load={load} ttlMs={10_000} />);
    await screen.findByText("불러오는 중");

    act(() => {
      invalidateResource("me:");
    });
    await screen.findByText("불러오는 중");

    // 끊긴 첫 번째 요청이 abort 이후 뒤늦게 거부돼도(fetch가 AbortError로 reject하는
    // 실제 상황을 흉내) 이미 새 시도(attempt)가 진행 중이므로 화면이 에러로 깜빡이지
    // 않고 새 시도의 결과로만 이어져야 한다.
    firstReject?.(new DOMException("aborted", "AbortError"));

    expect(await screen.findByText("새 값")).toBeInTheDocument();
    expect(screen.queryByText(/실패/)).not.toBeInTheDocument();
  });
});
