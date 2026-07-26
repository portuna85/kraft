import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { SavedNumbersClient } from "@/components/saved-numbers-client";

vi.mock("@/lib/device-token", () => ({
  getDeviceToken: () => "a".repeat(64),
}));

const SAVED_ITEM = {
  id: 1,
  numbers: [1, 2, 3, 4, 5, 6],
  label: null,
  source: "MANUAL",
  createdAt: "2026-01-01T00:00:00Z",
};

function mockFetch(handler: (url: string, init?: RequestInit) => { status: number; body: unknown }) {
  return vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    const { status, body } = handler(url, init);
    return Promise.resolve({
      ok: status >= 200 && status < 300,
      status,
      json: () => Promise.resolve(body),
    });
  });
}

const DELETE_UNDO_MS = 5000;

describe("저장 번호 화면", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("회차 선택 옵션은 최신 회차 포함 최근 20개로 제한된다", async () => {
    global.fetch = mockFetch((url) => {
      if (url.includes("/matches")) return { status: 200, body: [] };
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={1230} />);

    const select = await screen.findByLabelText("대조할 회차");
    const options = within(select).getAllByRole("option");
    // "latest" 옵션 1개 + 최근 20개 이하
    expect(options.length).toBeLessThanOrEqual(21);
  });

  it("회차를 바꾸면 이전 회차의 대조 결과를 즉시 비우고 로딩 문구를 보여준다", async () => {
    let matchCallCount = 0;
    global.fetch = mockFetch((url) => {
      if (url.includes("/matches")) {
        matchCallCount++;
        if (matchCallCount === 1) {
          return {
            status: 200,
            body: [
              {
                savedNumber: SAVED_ITEM,
                round: 1230,
                drawDate: "2026-01-01",
                drawNumbers: [1, 2, 3, 4, 5, 6],
                bonusNumber: 7,
                matchedCount: 6,
                bonusMatch: false,
                prizeTier: "1등",
              },
            ],
          };
        }
        return { status: 500, body: {} };
      }
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={1230} />);

    await waitFor(() => {
      expect(screen.getByText("1등")).toBeInTheDocument();
    });

    // 회차를 바꿔 매치 요청이 실패하게 만든다
    fireEvent.change(screen.getByLabelText("대조할 회차"), { target: { value: "1229" } });

    // 새 요청이 끝나기 전이라도 이전 회차의 당첨 배지는 즉시 사라지고 로딩 문구가 보여야 한다
    // (새 회차 결과로 오인되는 것을 방지 — P1-06).
    expect(screen.queryByText("1등")).not.toBeInTheDocument();
    expect(screen.getByText("대조 결과를 불러오는 중입니다.")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/대조 결과를 불러오지 못했습니다/)).toBeInTheDocument();
    });

    // 실패 후에도 이전 성공 결과(1등)를 되살리지 않는다 — 오해를 남기지 않기 위함.
    expect(screen.queryByText("1등")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("직접 입력한 회차를 적용하면 해당 회차로 대조를 요청한다", async () => {
    global.fetch = mockFetch((url) => {
      if (url.includes("/matches")) return { status: 200, body: [] };
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={1230} />);

    await screen.findByLabelText("대조할 회차");

    fireEvent.change(screen.getByLabelText("회차 직접 입력"), { target: { value: "500" } });
    fireEvent.click(screen.getByRole("button", { name: "적용" }));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenLastCalledWith(
        "/api/v1/saved/matches?round=500",
        expect.anything(),
      );
    });
  });

  it("범위를 벗어난 회차를 직접 입력하면 무시하고 이전 대조 요청을 유지한다", async () => {
    global.fetch = mockFetch((url) => {
      if (url.includes("/matches")) return { status: 200, body: [] };
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={1230} />);

    await screen.findByLabelText("대조할 회차");
    await waitFor(() => {
      expect(global.fetch).toHaveBeenLastCalledWith(
        "/api/v1/saved/matches?round=latest",
        expect.anything(),
      );
    });

    fireEvent.change(screen.getByLabelText("회차 직접 입력"), { target: { value: "1231" } });
    fireEvent.click(screen.getByRole("button", { name: "적용" }));

    // 최신 회차(1230)를 넘는 값은 무시되어 추가 요청이 나가지 않는다.
    expect(global.fetch).toHaveBeenLastCalledWith(
      "/api/v1/saved/matches?round=latest",
      expect.anything(),
    );
  });

  it("저장 번호를 불러오는 동안 로딩 문구를 보여준다", async () => {
    let resolveSaved: (() => void) | undefined;
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url === "/api/v1/saved") {
        return new Promise((resolve) => {
          resolveSaved = () =>
            resolve({ ok: true, status: 200, json: () => Promise.resolve([SAVED_ITEM]) });
        });
      }
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
    });

    render(<SavedNumbersClient latestRound={1230} />);

    expect(screen.getByText("저장된 번호를 불러오는 중입니다.")).toBeInTheDocument();

    resolveSaved?.();
    await waitFor(() => {
      expect(screen.queryByText("저장된 번호를 불러오는 중입니다.")).not.toBeInTheDocument();
    });
  });

  it("저장 번호 조회에 실패하면 오류 문구를 보여준다", async () => {
    global.fetch = mockFetch(() => ({ status: 500, body: {} }));

    render(<SavedNumbersClient latestRound={1230} />);

    await waitFor(() => {
      expect(
        screen.getByText("저장 번호를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.")
      ).toBeInTheDocument();
    });
  });

  it("저장한 번호가 없으면 안내 문구를 보여준다", async () => {
    global.fetch = mockFetch(() => ({ status: 200, body: [] }));

    render(<SavedNumbersClient latestRound={1230} />);

    await waitFor(() => {
      expect(
        screen.getByText("아직 저장한 번호가 없습니다. 추천 페이지에서 조합을 저장해 보세요.")
      ).toBeInTheDocument();
    });
  });

  it("최신 회차가 0이면 회차 대조 컨트롤을 렌더링하지 않는다", async () => {
    global.fetch = mockFetch((url) => {
      if (url.includes("/matches")) return { status: 200, body: [] };
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={0} />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" })).toBeInTheDocument();
    });
    expect(screen.queryByLabelText("대조할 회차")).not.toBeInTheDocument();
  });

  it("삭제를 누르면 실행 취소 상태가 되고, 유예 시간이 지나면 실제로 삭제된다", async () => {
    vi.useFakeTimers();
    let deleteUrl: string | null = null;
    global.fetch = mockFetch((url, init) => {
      if (init?.method === "DELETE") {
        deleteUrl = url;
        return { status: 204, body: null };
      }
      if (url.includes("/matches")) return { status: 200, body: [] };
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={1230} />);
    await vi.waitFor(() => {
      expect(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }));
    expect(screen.getByRole("button", { name: "실행 취소" })).toBeInTheDocument();
    expect(deleteUrl).toBeNull();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(DELETE_UNDO_MS);
    });

    expect(deleteUrl).toBe("/api/v1/saved/1");
    expect(screen.queryByRole("button", { name: "실행 취소" })).not.toBeInTheDocument();
    expect(screen.getByText("아직 저장한 번호가 없습니다. 추천 페이지에서 조합을 저장해 보세요.")).toBeInTheDocument();

    vi.useRealTimers();
  });

  it("유예 시간 내에 실행 취소를 누르면 삭제하지 않고 항목을 유지한다", async () => {
    vi.useFakeTimers();
    const fetchSpy = vi.fn();
    global.fetch = mockFetch((url, init) => {
      fetchSpy(url, init);
      if (init?.method === "DELETE") return { status: 204, body: null };
      if (url.includes("/matches")) return { status: 200, body: [] };
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={1230} />);
    await vi.waitFor(() => {
      expect(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }));
    fireEvent.click(screen.getByRole("button", { name: "실행 취소" }));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(DELETE_UNDO_MS);
    });

    expect(fetchSpy).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/saved/1"),
      expect.objectContaining({ method: "DELETE" })
    );
    expect(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" })).toBeInTheDocument();

    vi.useRealTimers();
  });

  it("삭제 요청이 실패하면 항목을 그대로 유지하고 대기 상태만 해제한다", async () => {
    vi.useFakeTimers();
    global.fetch = mockFetch((url, init) => {
      if (init?.method === "DELETE") return { status: 500, body: {} };
      if (url.includes("/matches")) return { status: 200, body: [] };
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={1230} />);
    await vi.waitFor(() => {
      expect(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(DELETE_UNDO_MS);
    });

    // 대기 상태는 풀리지만(실행 취소 버튼이 사라지고 다시 삭제 버튼으로 돌아옴) 항목은 남는다.
    expect(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "실행 취소" })).not.toBeInTheDocument();

    vi.useRealTimers();
  });
});
