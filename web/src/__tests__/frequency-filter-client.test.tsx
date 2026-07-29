import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { FrequencyFilterClient } from "@/components/frequency-filter-client";
import type { FrequencyStatsResponse } from "@/lib/api";

function ball(ballNumber: number, frequency: number) {
  return { ballNumber, frequency, lastRound: 1200 };
}

const INITIAL: FrequencyStatsResponse = {
  totalRounds: 100,
  frequencies: Array.from({ length: 45 }, (_, i) => ball(i + 1, 45 - i)),
  topSix: {
    balls: [1, 2, 3, 4, 5, 6].map((n) => ball(n, 45 - n + 1)),
    wonFirstPrize: true,
    firstPrizeHistory: [
      { round: 1, drawDate: "2002-12-07", firstPrizeAmount: 0 },
    ],
  },
  bottomSix: {
    balls: [40, 41, 42, 43, 44, 45].map((n) => ball(n, 45 - n + 1)),
    wonFirstPrize: false,
    firstPrizeHistory: [],
  },
};

function mockFetch(body: unknown, status = 200) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  });
}

describe("frequency 필터", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("초기 렌더에서 서버가 동봉한 당첨 이력을 바로 표시한다(별도 fetch 없음)", () => {
    global.fetch = mockFetch(INITIAL);

    render(<FrequencyFilterClient initial={INITIAL} />);

    expect(screen.getByText("역대 1등 당첨 이력 1건")).toBeInTheDocument();
    expect(screen.getByText("역대 1등 당첨 이력 없음")).toBeInTheDocument();
    expect(screen.getByText("2002년 12월 7일 토")).toBeInTheDocument();
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it("필터 조회 실패 시 오류와 재시도를 표시하고 기존 값을 유지한다", async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error("network down"));

    render(<FrequencyFilterClient initial={INITIAL} />);
    fireEvent.click(screen.getByRole("button", { name: "최근 100회" }));

    await waitFor(() => {
      expect(screen.getByText(/불러오지 못했습니다/)).toBeInTheDocument();
    });

    // 실패했으므로 초기 topSix/bottomSix가 그대로 유지된다
    expect(screen.getByText("역대 1등 당첨 이력 1건")).toBeInTheDocument();
  });

  it("재시도 클릭 시 실패했던 필터 값으로 다시 요청한다", async () => {
    const updated: FrequencyStatsResponse = {
      ...INITIAL,
      totalRounds: 100,
      topSix: { balls: INITIAL.topSix.balls, wonFirstPrize: false, firstPrizeHistory: [] },
    };
    global.fetch = vi.fn()
      .mockRejectedValueOnce(new Error("network down"))
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve(updated),
      });

    render(<FrequencyFilterClient initial={INITIAL} />);
    fireEvent.click(screen.getByRole("button", { name: "최근 200회" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledTimes(2);
    });
    expect(global.fetch).toHaveBeenLastCalledWith(
      "/api/v1/stats/frequency?limit=200",
      expect.anything(),
    );
  });

  it("전체 필터로 되돌리면 초기 값을 다시 표시한다", async () => {
    global.fetch = mockFetch({
      ...INITIAL,
      topSix: { balls: INITIAL.topSix.balls, wonFirstPrize: false, firstPrizeHistory: [] },
    });

    render(<FrequencyFilterClient initial={INITIAL} />);
    fireEvent.click(screen.getByRole("button", { name: "최근 100회" }));

    await waitFor(() => {
      expect(screen.getAllByText("역대 1등 당첨 이력 없음")).toHaveLength(2);
    });

    fireEvent.click(screen.getByRole("button", { name: "전체" }));

    expect(screen.getByText("역대 1등 당첨 이력 1건")).toBeInTheDocument();
    expect(screen.getAllByText("역대 1등 당첨 이력 없음")).toHaveLength(1);
  });
});
