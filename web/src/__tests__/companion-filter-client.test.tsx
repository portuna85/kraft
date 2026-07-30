import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CompanionFilterClient } from "@/app/companion/companion-filter-client";

const INITIAL_PAIRS = [
  { ballA: 1, ballB: 2, coCount: 10 },
  { ballA: 3, ballB: 4, coCount: 5 },
];

function renderClient() {
  return render(<CompanionFilterClient pairs={INITIAL_PAIRS} totalRounds={100} />);
}

function selectBall(number: number) {
  fireEvent.click(screen.getByRole("button", { name: String(number), pressed: false }));
}

describe("동반 출현 필터", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("초기에는 전달받은 상위 목록을 표시한다", () => {
    renderClient();

    expect(screen.getByText("10회 동반 출현")).toBeInTheDocument();
    expect(screen.getByText("5회 동반 출현")).toBeInTheDocument();
  });

  it("전체 조합 fetch가 실패하면 기록 없음 대신 오류와 재시도를 표시한다", async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error("network down"));

    renderClient();
    selectBall(7);

    await waitFor(() => {
      expect(screen.getByText(/데이터를 불러오지 못했습니다/)).toBeInTheDocument();
    });

    expect(screen.queryByText("해당 번호를 포함한 동반 출현 기록이 없습니다.")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("재시도 클릭 시 다시 요청한다", async () => {
    global.fetch = vi.fn()
      .mockRejectedValueOnce(new Error("network down"))
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ totalRounds: 100, topPairs: [{ ballA: 7, ballB: 9, coCount: 3 }] }),
      });

    renderClient();
    selectBall(7);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    await waitFor(() => {
      expect(screen.getByText("3회 동반 출현")).toBeInTheDocument();
    });

    expect(global.fetch).toHaveBeenCalledTimes(2);
    expect(global.fetch).toHaveBeenLastCalledWith(
      "/api/v1/stats/companion?ball=7",
      expect.objectContaining({ signal: expect.anything() }),
    );
  });

  it("성공 후 빈 결과일 때만 기록 없음을 표시한다", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ totalRounds: 100, topPairs: [] }),
    });

    renderClient();
    selectBall(7);

    await waitFor(() => {
      expect(screen.getByText("해당 번호를 포함한 동반 출현 기록이 없습니다.")).toBeInTheDocument();
    });
  });

  it("필터 해제 시 초기 목록으로 돌아간다", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ totalRounds: 100, topPairs: [{ ballA: 7, ballB: 9, coCount: 3 }] }),
    });

    renderClient();
    selectBall(7);

    await waitFor(() => {
      expect(screen.getByText("3회 동반 출현")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "필터 해제" }));

    expect(screen.getByText("10회 동반 출현")).toBeInTheDocument();
    expect(screen.queryByText("3회 동반 출현")).not.toBeInTheDocument();
  });
});
