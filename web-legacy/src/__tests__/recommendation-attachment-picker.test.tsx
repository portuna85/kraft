import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { RecommendationAttachmentPicker } from "@/features/community/recommendation-attachment-picker";

const SETS = {
  items: [
    {
      id: 5,
      strategy: "balanced",
      algorithmVersion: "v1",
      historyThroughRound: 1230,
      exclusionPolicyVersion: "v1",
      lockedNumbers: [],
      excludedNumbers: [],
      createdAt: "2026-01-03T12:00:00Z",
      items: [{ position: 1, numbers: [1, 2, 3, 4, 5, 6], explanationCodes: [] }],
    },
  ],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
};

// FE-053: 로딩 중·조회 실패·세트 0개가 모두 null 렌더라 "첨부 기능이 없는 화면"으로 보였다.
describe("추천 세트 첨부 선택기", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("추천 세트가 있으면 첨부 라디오를 보여준다", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(SETS),
    });

    render(<RecommendationAttachmentPicker value={null} onChange={() => {}} />);

    expect(await screen.findByText("첨부 안 함")).toBeInTheDocument();
  });

  it("조회에 실패하면 빈 상태가 아니라 실패와 재시도를 보여준다", async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error("network down"));

    render(<RecommendationAttachmentPicker value={null} onChange={() => {}} />);

    expect(await screen.findByText("추천 이력을 불러오지 못했습니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
    expect(screen.queryByText(/첨부할 추천 세트가 없습니다/)).not.toBeInTheDocument();
  });

  it("추천 세트가 없으면 사라지지 않고 빈 상태를 설명한다", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ ...SETS, items: [], totalElements: 0 }),
    });

    render(<RecommendationAttachmentPicker value={null} onChange={() => {}} />);

    expect(await screen.findByText(/첨부할 추천 세트가 없습니다/)).toBeInTheDocument();
  });

  it("실패 후 다시 시도하면 목록을 다시 불러온다", async () => {
    let shouldFail = true;
    global.fetch = vi.fn().mockImplementation(() => {
      if (shouldFail) return Promise.reject(new Error("network down"));
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SETS) });
    });

    render(<RecommendationAttachmentPicker value={null} onChange={() => {}} />);

    const retryButton = await screen.findByRole("button", { name: "다시 시도" });
    shouldFail = false;
    fireEvent.click(retryButton);

    await waitFor(() => {
      expect(screen.getByText("첨부 안 함")).toBeInTheDocument();
    });
  });
});
