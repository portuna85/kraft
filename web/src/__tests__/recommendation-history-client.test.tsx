import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { RecommendationHistoryClient } from "@/features/recommendation/recommendation-history-client";

function makeSet(id: number) {
  return {
    id,
    strategy: "balanced" as const,
    algorithmVersion: "v1",
    historyThroughRound: 1230,
    exclusionPolicyVersion: "v1",
    lockedNumbers: [],
    excludedNumbers: [],
    createdAt: "2026-01-03T12:00:00Z",
    items: [{ position: 1, numbers: [1, 2, 3, 4, 5, 6], explanationCodes: [] }],
  };
}

function pageResponse(page: number, totalElements: number, totalPages: number, ids: number[]) {
  return { items: ids.map(makeSet), page, size: 20, totalElements, totalPages };
}

describe("추천 이력 화면", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("생성 일시를 표시한다", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(pageResponse(0, 1, 1, [1])),
    });

    render(<RecommendationHistoryClient />);

    await waitFor(() => {
      expect(document.querySelector("time[datetime='2026-01-03T12:00:00Z']")).not.toBeNull();
    });
  });

  // FE-024: DB 식별자만 있던 aria-label로는 어떤 항목인지 알 수 없었다.
  it("삭제 버튼 이름에 DB 식별자 대신 전략과 시각을 쓴다", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(pageResponse(0, 1, 1, [4821])),
    });

    render(<RecommendationHistoryClient />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /균형 조합.*추천 세트 삭제/ })).toBeInTheDocument();
    });
    expect(screen.queryByRole("button", { name: "추천 세트 4821 삭제" })).not.toBeInTheDocument();
  });

  // FE-021: 51번째부터는 접근 경로도 안내도 없었다.
  it("다음 페이지가 있으면 더 보기로 이어서 불러온다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string) => {
      const body = url.includes("page=1")
        ? pageResponse(1, 3, 2, [3])
        : pageResponse(0, 3, 2, [1, 2]);
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(body) });
    });

    render(<RecommendationHistoryClient />);

    const more = await screen.findByRole("button", { name: "더 보기" });
    expect(screen.getByText(/전체 3건 중 2건 표시/)).toBeInTheDocument();

    fireEvent.click(more);

    await waitFor(() => {
      expect(screen.getByText(/전체 3건을 모두 표시했습니다/)).toBeInTheDocument();
    });
    expect(screen.getAllByRole("button", { name: /추천 세트 삭제/ })).toHaveLength(3);
  });

  it("마지막 페이지에서는 더 보기를 보여주지 않는다", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(pageResponse(0, 1, 1, [1])),
    });

    render(<RecommendationHistoryClient />);

    await screen.findByText(/전체 1건을 모두 표시했습니다/);
    expect(screen.queryByRole("button", { name: "더 보기" })).not.toBeInTheDocument();
  });

  // FE-036: items와 totalElements를 따로 들면 삭제 후 목록과 건수가 어긋난다.
  // 한 상태로 묶었으므로 함께 갱신되는지 확인한다.
  it("삭제하면 목록과 전체 건수가 함께 줄어든다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (init?.method === "DELETE") {
        return Promise.resolve({ ok: true, status: 204, json: () => Promise.resolve(null) });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve(pageResponse(0, 2, 1, [1, 2])),
      });
    });

    render(<RecommendationHistoryClient />);

    await screen.findByText(/전체 2건을 모두 표시했습니다/);
    fireEvent.click(screen.getAllByRole("button", { name: /추천 세트 삭제/ })[0]);
    // FE-003: 삭제는 확인 다이얼로그를 거친다.
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }));

    await waitFor(() => {
      expect(screen.getByText(/전체 1건을 모두 표시했습니다/)).toBeInTheDocument();
    });
    expect(screen.getAllByRole("button", { name: /추천 세트 삭제/ })).toHaveLength(1);
  });

  it("삭제를 누르면 확인 전에는 요청을 보내지 않는다", async () => {
    const fetchSpy = vi.fn();
    global.fetch = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      fetchSpy(url, init);
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve(pageResponse(0, 1, 1, [1])),
      });
    });

    render(<RecommendationHistoryClient />);
    fireEvent.click(await screen.findByRole("button", { name: /추천 세트 삭제/ }));

    expect(screen.getByRole("dialog")).toHaveTextContent("추천 세트를 삭제할까요?");
    expect(fetchSpy).not.toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ method: "DELETE" })
    );
  });

  it("더 보기 실패는 기존 목록을 지우지 않고 오류만 알린다", async () => {
    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes("page=1")) return Promise.reject(new Error("network down"));
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve(pageResponse(0, 3, 2, [1, 2])),
      });
    });

    render(<RecommendationHistoryClient />);

    fireEvent.click(await screen.findByRole("button", { name: "더 보기" }));

    await waitFor(() => {
      expect(screen.getByText(/다음 목록을 불러오지 못했습니다/)).toBeInTheDocument();
    });
    expect(screen.getAllByRole("button", { name: /추천 세트 삭제/ })).toHaveLength(2);
  });
});
