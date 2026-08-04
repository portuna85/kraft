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


describe("저장 번호 화면", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // FE-042: 최신 회차 조회(SSR)가 실패하면 latestRound가 0이 되어 회차 컨트롤이 통째로
  // 사라졌다. 저장 목록은 보이는데 대조 기능만 이유 없이 없어져 고장인지 알 수 없었다.
  it("최신 회차를 알 수 없으면 대조를 할 수 없는 이유를 알린다", async () => {
    global.fetch = mockFetch((url) => {
      if (url.includes("/matches")) return { status: 200, body: [] };
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={0} />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("회차 정보를 불러오지 못해");
    });
    // 저장 목록 자체는 그대로 보여야 한다.
    expect(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" })).toBeInTheDocument();
    expect(screen.queryByLabelText("대조할 회차")).not.toBeInTheDocument();
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

    expect(screen.getByLabelText("저장된 번호를 불러오는 중")).toBeInTheDocument();

    resolveSaved?.();
    await waitFor(() => {
      expect(screen.queryByLabelText("저장된 번호를 불러오는 중")).not.toBeInTheDocument();
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


  // FE-003: 5초 유예 undo를 확인 다이얼로그로 대체했다. 유예 방식은 타이머·대기 상태·
  // 이탈 시 flush(FE-045)까지 따라붙었는데, 확인이 선행되면 그 복잡도가 통째로 사라진다.
  it("삭제를 누르면 바로 지우지 않고 확인을 먼저 묻는다", async () => {
    const fetchSpy = vi.fn();
    global.fetch = mockFetch((url, init) => {
      fetchSpy(url, init);
      if (init?.method === "DELETE") return { status: 204, body: null };
      if (url.includes("/matches")) return { status: 200, body: [] };
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={1230} />);
    fireEvent.click(await screen.findByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }));

    expect(screen.getByRole("dialog")).toHaveTextContent("저장 번호를 삭제할까요?");
    expect(fetchSpy).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/saved/1"),
      expect.objectContaining({ method: "DELETE" })
    );
  });

  it("확인하면 삭제하고 목록에서 지운다", async () => {
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
    fireEvent.click(await screen.findByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }));

    await waitFor(() => {
      expect(deleteUrl).toBe("/api/v1/saved/1");
    });
    await waitFor(() => {
      expect(
        screen.getByText("아직 저장한 번호가 없습니다. 추천 페이지에서 조합을 저장해 보세요.")
      ).toBeInTheDocument();
    });
  });

  it("취소하면 삭제 요청을 보내지 않고 항목을 유지한다", async () => {
    const fetchSpy = vi.fn();
    global.fetch = mockFetch((url, init) => {
      fetchSpy(url, init);
      if (init?.method === "DELETE") return { status: 204, body: null };
      if (url.includes("/matches")) return { status: 200, body: [] };
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={1230} />);
    fireEvent.click(await screen.findByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "취소" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/saved/1"),
      expect.objectContaining({ method: "DELETE" })
    );
    expect(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" })).toBeInTheDocument();
  });

  it("삭제에 실패하면 다이얼로그 안에서 알리고 항목을 유지한다", async () => {
    global.fetch = mockFetch((url, init) => {
      if (init?.method === "DELETE") return { status: 500, body: {} };
      if (url.includes("/matches")) return { status: 200, body: [] };
      return { status: 200, body: [SAVED_ITEM] };
    });

    render(<SavedNumbersClient latestRound={1230} />);
    fireEvent.click(await screen.findByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }));

    // 닫아 버리면 사용자가 결과를 못 보므로 다이얼로그를 열어 둔 채 이유를 알린다.
    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("저장 번호를 삭제하지 못했습니다");
    });
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    // 다이얼로그가 열려 있는 동안 배경은 inert라 role 조회에서 빠진다 — 닫고 확인한다.
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "취소" }));
    expect(screen.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" })).toBeInTheDocument();
  });
});
