import { render, screen, waitFor } from "@testing-library/react";
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

    invalidateResource("me:");

    expect(await screen.findByText("이후")).toBeInTheDocument();
  });

  it("무효화 접두사가 다르면 재조회하지 않는다", async () => {
    const load = vi.fn().mockResolvedValue("값");

    render(<Probe cacheKey="me:saved" load={load} ttlMs={10_000} />);
    expect(await screen.findByText("값")).toBeInTheDocument();

    invalidateResource("community:");

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
});
